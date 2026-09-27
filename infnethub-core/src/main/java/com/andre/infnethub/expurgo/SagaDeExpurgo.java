package com.andre.infnethub.expurgo;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.comando.EnviarAvisoV1;
import com.andre.infnethub.exception.ResourceNotFoundException;
import com.andre.infnethub.mensageria.CaixaDeSaida;
import com.andre.infnethub.mensageria.EventosDeUsuario;
import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Conduz a remoção de uma pessoa como um processo, e não como um {@code DELETE}.
 *
 * <h2>O problema</h2>
 * <p>Os dados de um aluno estão em três bancos que nenhuma transação alcança ao
 * mesmo tempo. Apagar no core e publicar um evento resolveria o caso feliz e
 * deixaria, no caso infeliz, um estado que ninguém sabe descrever: removido
 * aqui, presente ali, sem registro de onde parou.
 *
 * <h2>A saga, coreografada</h2>
 * <pre>
 *   1. solicitar   core bloqueia a pessoa  →  publica ExpurgoSolicitado
 *   2.             boletim anonimiza       →  publica AlunoAnonimizado
 *                  …ou recusa, com motivo  →  publica AnonimizacaoRecusada
 *   3a. concluir   core anonimiza o cadastro, publica UsuarioRemovido (fato consumado)
 *   3b. reverter   core DESFAZ o bloqueio e avisa quem pediu     ← compensação
 * </pre>
 *
 * <p>Coreografada, e não orquestrada: não há um maestro chamando cada
 * participante na ordem. Cada um reage ao que ouve e anuncia o que fez. O preço
 * é que o fluxo não está escrito em lugar nenhum por inteiro — está distribuído
 * entre quem publica e quem consome, e é por isso que ele aparece desenhado
 * aqui em cima e no relatório.
 *
 * <h2>Por que a compensação é possível</h2>
 * <p>Porque o passo 1 <strong>não apaga nada</strong>. Ele bloqueia: a pessoa
 * some da API, não entra e não age, mas o registro continua no banco. Só o
 * passo 3a é irreversível — e ele só acontece depois de todos confirmarem. Uma
 * saga em que o primeiro passo já é irreversível não tem compensação; tem
 * escalada para tratamento humano, que é bem pior.
 */
@Service
@RequiredArgsConstructor
public class SagaDeExpurgo {

    private static final Logger log = LoggerFactory.getLogger(SagaDeExpurgo.class);

    private final UsuarioRepository usuarios;
    private final ExpurgoRepository expurgos;
    private final EventosDeUsuario eventos;
    private final CaixaDeSaida caixa;
    private final AnonimizacaoDaTrilha trilha;

    /**
     * Passo 1 — bloqueia a pessoa e anuncia o pedido.
     *
     * <p>Tudo numa transação: o bloqueio e o evento entram juntos ou não entram.
     * Se o evento saísse sem o bloqueio, o boletim anonimizaria os dados de
     * alguém que continua usando o sistema.
     *
     * @return o processo criado.
     */
    @Transactional
    public ExpurgoLgpd solicitar(Long usuarioId, Long solicitadoPor) {
        // A ordem das duas verificações importa, e não é a óbvia. Quem já está
        // bloqueado por uma saga em andamento também está "removido" para a
        // API — checar a existência primeiro responderia 404 a um segundo
        // pedido, escondendo a informação útil, que é "já está saindo".
        expurgos.findByUsuarioIdAndEstado(usuarioId, EstadoDoExpurgo.SOLICITADO).ifPresent(emCurso -> {
            throw new ExpurgoEmAndamentoException(usuarioId, emCurso.getSolicitadoEm());
        });

        // Passada essa, "removido" só pode significar saga concluída: a pessoa
        // foi anonimizada e não há o que remover de novo.
        Usuario usuario = usuarios.findById(usuarioId)
                .filter(u -> !u.isRemovido())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com id: " + usuarioId));

        usuario.setRemovido(true);
        usuarios.save(usuario);

        ExpurgoLgpd processo = expurgos.save(new ExpurgoLgpd(usuarioId, solicitadoPor));
        eventos.expurgoSolicitado(processo.getId(), usuarioId);

        log.info("expurgo {} aberto para o usuário {} a pedido de {}",
                processo.getId(), usuarioId, solicitadoPor);
        return processo;
    }

    /**
     * Passo 3a — todos confirmaram: o cadastro é anonimizado, e agora sim é
     * definitivo.
     *
     * <p>Anonimizar, e não apagar a linha: a pessoa pode ter publicado no feed,
     * e apagar o autor exigiria apagar junto o que outras pessoas comentaram.
     * O dado anonimizado sai do alcance da LGPD (art. 12) e o conteúdo continua
     * de pé, sem autor identificado. A trilha de auditoria é anonimizada na
     * mesma transação — ver {@link AnonimizacaoDaTrilha}.
     *
     * <p>{@code UsuarioRemovidoV1} só é publicado aqui — é o <em>fato
     * consumado</em>. Os serviços que apagam dados ao recebê-lo (o de
     * notificação apaga o histórico da pessoa) precisam dessa garantia: se
     * agissem sobre o pedido, teriam apagado dados de alguém cuja remoção ainda
     * podia ser revertida.
     *
     * <h3>A confirmação que chega tarde demais</h3>
     * <p>Se o processo já foi revertido — por prazo vencido —, o participante
     * anonimizou os dados de alguém que continua ativo. Ignorar a mensagem
     * deixaria essa diferença para sempre. O core responde reenviando o estado
     * atual da pessoa, e o participante, que aplica o que recebe, a restaura.
     * É a compensação da compensação, e é o que torna seguro ter prazo.
     */
    @Transactional
    public void concluir(Long processoId, Long usuarioId, int registrosMantidos) {
        ExpurgoLgpd processo = processoTravado(processoId, usuarioId);
        if (processo == null) {
            return;
        }
        if (!processo.emAndamento()) {
            if (processo.getEstado() == EstadoDoExpurgo.REVERTIDO) {
                restaurarNosParticipantes(processo);
            } else {
                // Reentrega de uma confirmação já aplicada. Encerrar de novo é
                // que seria errado.
                log.info("confirmação do expurgo {} repetida; ignorada", processoId);
            }
            return;
        }

        usuarios.findById(usuarioId).ifPresent(usuario -> {
            usuario.anonimizar();
            usuarios.save(usuario);
        });
        int naTrilha = trilha.anonimizar(usuarioId);

        processo.concluir(registrosMantidos);
        eventos.removido(usuarioId);

        log.info("expurgo {} do usuário {} concluído; {} registro(s) acadêmico(s) mantido(s) e "
                        + "{} linha(s) da trilha de auditoria anonimizada(s)",
                processoId, usuarioId, registrosMantidos, naTrilha);
    }

    /**
     * Passo 3b — a compensação: um participante recusou, e o bloqueio é desfeito.
     *
     * <p>A pessoa volta a existir, e quem pediu a remoção precisa ficar sabendo
     * — senão veria o cadastro reaparecer sem explicação. O aviso vai pelo
     * mesmo caminho de qualquer outro aviso do sistema: um comando na caixa de
     * saída, entregue pelo serviço de notificação.
     */
    @Transactional
    public void reverter(Long processoId, Long usuarioId, String motivo) {
        ExpurgoLgpd processo = processoTravado(processoId, usuarioId);
        if (processo == null) {
            return;
        }
        if (!processo.emAndamento()) {
            log.info("recusa do expurgo {} com o processo já {}; ignorada", processoId, processo.getEstado());
            return;
        }
        compensar(processo, motivo);
    }

    /**
     * O prazo — uma saga sem ele pode reter um cadastro bloqueado para sempre.
     *
     * <p>Se o participante não responde (fora do ar, ou com a mensagem retida
     * numa fila de mensagens mortas), a pessoa ficaria indefinidamente sem
     * existir e sem ter sido removida. Vencido o prazo, o bloqueio é desfeito
     * pela mesma compensação da recusa, com o motivo dito como é.
     *
     * <p>Desfazer por prazo é seguro porque a resposta que chegar depois não é
     * perdida: ver "A confirmação que chega tarde demais", em
     * {@link #concluir(Long, Long, int)}.
     *
     * @return quantos processos foram encerrados por prazo.
     */
    @Transactional
    public int expirarAbertosAntesDe(LocalDateTime limite, Duration prazo) {
        List<ExpurgoLgpd> vencidos =
                expurgos.findByEstadoAndSolicitadoEmBefore(EstadoDoExpurgo.SOLICITADO, limite);
        int encerrados = 0;
        for (ExpurgoLgpd candidato : vencidos) {
            ExpurgoLgpd processo = processoTravado(candidato.getId(), candidato.getUsuarioId());
            // Entre a consulta e a trava, a resposta pode ter chegado.
            if (processo != null && processo.emAndamento()) {
                compensar(processo, "os serviços participantes não responderam em %s; o cadastro foi mantido"
                        .formatted(descrever(prazo)));
                encerrados++;
            }
        }
        return encerrados;
    }

    private void compensar(ExpurgoLgpd processo, String motivo) {
        usuarios.findById(processo.getUsuarioId()).ifPresent(usuario -> {
            usuario.setRemovido(false);
            usuarios.save(usuario);
        });

        processo.reverter(motivo);
        avisarQuemPediu(processo, motivo);

        log.warn("expurgo {} do usuário {} revertido: {}", processo.getId(), processo.getUsuarioId(), motivo);
    }

    private void restaurarNosParticipantes(ExpurgoLgpd processo) {
        usuarios.findById(processo.getUsuarioId())
                .filter(u -> !u.isRemovido())
                .ifPresent(usuario -> {
                    eventos.atualizado(usuario);
                    log.warn("expurgo {} já revertido recebeu confirmação tardia; estado do usuário {} "
                            + "reenviado para os participantes restaurarem", processo.getId(), usuario.getId());
                });
    }

    /**
     * O processo, travado para esta transação — ou {@code null} se a mensagem
     * não corresponde a nenhum.
     *
     * <p>A trava existe porque o prazo e a resposta podem chegar ao mesmo
     * tempo: sem ela, os dois leriam {@code SOLICITADO} e a pessoa seria
     * anonimizada e reativada na mesma saga.
     */
    private ExpurgoLgpd processoTravado(Long processoId, Long usuarioId) {
        ExpurgoLgpd processo = expurgos.findComTravaById(processoId).orElse(null);
        if (processo == null || !processo.getUsuarioId().equals(usuarioId)) {
            log.warn("resposta para o expurgo {} do usuário {} não corresponde a nenhum processo; ignorada",
                    processoId, usuarioId);
            return null;
        }
        return processo;
    }

    private static String descrever(Duration prazo) {
        if (prazo.toDays() > 0 && prazo.toHoursPart() == 0 && prazo.toMinutesPart() == 0) {
            return prazo.toDays() + " dia(s)";
        }
        if (prazo.toHours() > 0 && prazo.toMinutesPart() == 0) {
            return prazo.toHours() + " hora(s)";
        }
        if (prazo.toMinutes() > 0 && prazo.toSecondsPart() == 0) {
            return prazo.toMinutes() + " minuto(s)";
        }
        return prazo.toSeconds() + " segundo(s)";
    }

    private void avisarQuemPediu(ExpurgoLgpd processo, String motivo) {
        if (processo.getSolicitadoPor() == null) {
            return;
        }
        caixa.depositar(new EnviarAvisoV1(
                UUID.randomUUID(),
                Instant.now(),
                List.of(processo.getSolicitadoPor()),
                "A remoção do usuário %d não pôde ser concluída: %s. O cadastro foi reativado."
                        .formatted(processo.getUsuarioId(), motivo),
                // sem link: o front-end não tem página de cadastro de usuário,
                // e "/usuarios/{id}" é endereço da API, não de uma tela
                null,
                processo.getSolicitadoPor()),
                Canais.ROTA_ENVIAR_AVISO);
    }

    @Transactional(readOnly = true)
    public List<ExpurgoLgpd> historicoDe(Long usuarioId) {
        return expurgos.findByUsuarioIdOrderByIdDesc(usuarioId);
    }

    /** Pedir a remoção de quem já está saindo não é erro do sistema; é do pedido. */
    public static class ExpurgoEmAndamentoException extends IllegalStateException {
        public ExpurgoEmAndamentoException(Long usuarioId, java.time.LocalDateTime desde) {
            super("Já existe um expurgo em andamento para o usuário %d, aberto em %s".formatted(usuarioId, desde));
        }
    }
}
