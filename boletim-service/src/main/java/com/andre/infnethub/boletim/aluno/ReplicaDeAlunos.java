package com.andre.infnethub.boletim.aluno;

import com.andre.infnethub.boletim.dto.AlunoDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantém a réplica e responde por ela.
 *
 * <p>Substitui o cliente Feign do TP3. A diferença para quem chama é nenhuma —
 * continua recebendo um {@link AlunoDTO} —, e a diferença para o sistema é
 * toda: a leitura é local, não passa pela rede e não depende de o core estar
 * no ar.
 */
@Service
@RequiredArgsConstructor
public class ReplicaDeAlunos {

    private static final Logger log = LoggerFactory.getLogger(ReplicaDeAlunos.class);

    /** O único papel que tem boletim. */
    static final String PAPEL_ALUNO = "ALUNO";

    private final AlunoReplicaRepository repositorio;

    @Transactional(readOnly = true)
    public AlunoDTO buscar(Long alunoId) {
        return repositorio.findById(alunoId)
                .map(AlunoDTO::de)
                .orElseGet(() -> AlunoDTO.indisponivel(alunoId));
    }

    /**
     * Aplica o estado que chegou do core, se ele for mais novo que o guardado.
     *
     * <p><strong>Por que comparar versões, se a fila entrega em ordem?</strong>
     * Porque "em ordem" vale para o caminho feliz. Uma mensagem que falhou e foi
     * reprocessada da fila de mensagens mortas chega depois das que vieram
     * atrás dela. Sem a comparação, ela sobrescreveria o nome novo com o velho —
     * e ninguém perceberia.
     *
     * <p><strong>Só alunos são replicados.</strong> Professores e secretaria não
     * têm boletim; guardar os dados deles aqui seria reter dado pessoal sem
     * finalidade. Um usuário que deixa de ser aluno sai da réplica.
     *
     * @return {@code true} se a réplica mudou.
     */
    @Transactional
    public boolean aplicar(AlunoReplica recebido) {
        var atual = repositorio.findById(recebido.getId());

        if (atual.isPresent() && !recebido.maisNovoQue(atual.get())) {
            log.info("aluno {}: versão {} ignorada, a réplica já está na {}",
                    recebido.getId(), recebido.getVersaoOrigem(), atual.get().getVersaoOrigem());
            return false;
        }

        if (!PAPEL_ALUNO.equals(recebido.getPapel())) {
            atual.ifPresent(r -> {
                repositorio.delete(r);
                log.info("usuário {} deixou de ser aluno; removido da réplica", r.getId());
            });
            return atual.isPresent();
        }

        repositorio.save(recebido);
        log.info("aluno {} sincronizado na versão {}", recebido.getId(), recebido.getVersaoOrigem());
        return true;
    }

    @Transactional
    public boolean remover(Long alunoId) {
        if (!repositorio.existsById(alunoId)) {
            return false;
        }
        repositorio.deleteById(alunoId);
        log.info("aluno {} removido da réplica", alunoId);
        return true;
    }
}
