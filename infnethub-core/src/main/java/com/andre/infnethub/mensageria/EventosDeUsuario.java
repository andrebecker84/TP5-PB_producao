package com.andre.infnethub.mensageria;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.expurgo.ExpurgoSolicitadoV1;
import com.andre.infnethub.contratos.usuario.UsuarioAtualizadoV1;
import com.andre.infnethub.contratos.usuario.UsuarioCadastradoV1;
import com.andre.infnethub.contratos.usuario.UsuarioRemovidoV1;
import com.andre.infnethub.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Traduz o que aconteceu com um {@link Usuario} para os contratos públicos.
 *
 * <p>A tradução é o ponto onde o modelo interno encontra o contrato — e onde
 * ele é protegido: a entidade pode ganhar campos, mudar nomes ou se reorganizar
 * sem que nenhum consumidor perceba, desde que este mapeamento continue
 * produzindo o mesmo evento.
 *
 * <p>Deve ser chamado depois de o usuário ter sido gravado (e, na atualização,
 * sincronizado com o banco), para que a {@code versao} publicada seja a
 * definitiva.
 */
@Component
@RequiredArgsConstructor
public class EventosDeUsuario {

    private final CaixaDeSaida caixa;

    public void cadastrado(Usuario u) {
        caixa.depositar(new UsuarioCadastradoV1(
                UUID.randomUUID(), Instant.now(), u.getId(), versaoDe(u),
                u.getNome(), u.getEscola(), u.getUltimoBloco(), u.getClasse(),
                u.getPapel().name(), u.getPapel().getDescricao()),
                Canais.ROTA_USUARIO_CADASTRADO);
    }

    public void atualizado(Usuario u) {
        caixa.depositar(new UsuarioAtualizadoV1(
                UUID.randomUUID(), Instant.now(), u.getId(), versaoDe(u),
                u.getNome(), u.getEscola(), u.getUltimoBloco(), u.getClasse(),
                u.getPapel().name(), u.getPapel().getDescricao()),
                Canais.ROTA_USUARIO_ATUALIZADO);
    }

    /**
     * Abre a saga de expurgo: a pessoa foi bloqueada e os participantes devem
     * remover o que têm sobre ela.
     */
    public void expurgoSolicitado(Long processoId, Long usuarioId) {
        caixa.depositar(new ExpurgoSolicitadoV1(UUID.randomUUID(), Instant.now(), processoId, usuarioId),
                Canais.ROTA_EXPURGO_SOLICITADO);
    }

    /**
     * O fato consumado, publicado <strong>só quando a saga conclui</strong>.
     *
     * <p>A distinção entre este evento e {@link #expurgoSolicitado(Long, Long)} é o
     * que impede um consumidor de apagar dados cedo demais. Quem reage a este
     * aqui pode apagar sem medo: não há mais volta.
     */
    public void removido(Long usuarioId) {
        caixa.depositar(new UsuarioRemovidoV1(UUID.randomUUID(), Instant.now(), usuarioId),
                Canais.ROTA_USUARIO_REMOVIDO);
    }

    private static long versaoDe(Usuario u) {
        return u.getVersao() == null ? 0L : u.getVersao();
    }
}
