package com.andre.infnethub.service;

import com.andre.infnethub.auditoria.RevisaoAuditoria;
import com.andre.infnethub.dto.historico.*;
import com.andre.infnethub.exception.ResourceNotFoundException;
import com.andre.infnethub.model.Comentario;
import com.andre.infnethub.model.Post;
import com.andre.infnethub.model.Usuario;
import com.andre.infnethub.model.Vaga;
import com.andre.infnethub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.history.Revision;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;

/**
 * Consulta do histórico de alterações.
 *
 * <p>Toda a leitura passa pelos {@link RevisionRepository} do Spring Data
 * Envers: nenhuma consulta às tabelas {@code _aud} é escrita à mão.
 *
 * <p>Os métodos são transacionais porque as associações do snapshot (o autor de
 * um post, por exemplo) são resolvidas sob demanda contra {@code usuarios_aud};
 * fora de uma transação, com {@code open-in-view=false}, essa navegação falharia.
 */
@Service
@RequiredArgsConstructor
public class HistoricoService {

    private final UsuarioRepository           usuarioRepository;
    private final PostRepository              postRepository;
    private final VagaRepository              vagaRepository;
    private final ComentarioRepository        comentarioRepository;
    private final RevisaoAuditoriaRepository  revisaoAuditoriaRepository;

    // ── Histórico por entidade ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaginaDTO<RevisaoDTO<UsuarioSnapshot>> historicoUsuario(Long id, Pageable pageable) {
        return historico(usuarioRepository, id, pageable, UsuarioSnapshot::de, "Usuário");
    }

    @Transactional(readOnly = true)
    public PaginaDTO<RevisaoDTO<PostSnapshot>> historicoPost(Long id, Pageable pageable) {
        return historico(postRepository, id, pageable, PostSnapshot::de, "Post");
    }

    @Transactional(readOnly = true)
    public PaginaDTO<RevisaoDTO<VagaSnapshot>> historicoVaga(Long id, Pageable pageable) {
        return historico(vagaRepository, id, pageable, VagaSnapshot::de, "Vaga");
    }

    @Transactional(readOnly = true)
    public PaginaDTO<RevisaoDTO<ComentarioSnapshot>> historicoComentario(Long id, Pageable pageable) {
        return historico(comentarioRepository, id, pageable, ComentarioSnapshot::de, "Comentário");
    }

    // ── Revisão específica ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RevisaoDTO<UsuarioSnapshot> revisaoUsuario(Long id, Integer revisao) {
        return revisao(usuarioRepository, id, revisao, UsuarioSnapshot::de, "Usuário");
    }

    @Transactional(readOnly = true)
    public RevisaoDTO<PostSnapshot> revisaoPost(Long id, Integer revisao) {
        return revisao(postRepository, id, revisao, PostSnapshot::de, "Post");
    }

    @Transactional(readOnly = true)
    public RevisaoDTO<VagaSnapshot> revisaoVaga(Long id, Integer revisao) {
        return revisao(vagaRepository, id, revisao, VagaSnapshot::de, "Vaga");
    }

    @Transactional(readOnly = true)
    public RevisaoDTO<ComentarioSnapshot> revisaoComentario(Long id, Integer revisao) {
        return revisao(comentarioRepository, id, revisao, ComentarioSnapshot::de, "Comentário");
    }

    // ── Estado mais recente registrado ─────────────────────────────────────

    /**
     * Última revisão conhecida de um post — inclusive se ele já foi excluído.
     *
     * <p>É o caso de uso que justifica {@code store_data_at_delete}: recuperar o
     * conteúdo de um post que não existe mais em {@code posts}.
     */
    @Transactional(readOnly = true)
    public RevisaoDTO<PostSnapshot> ultimaRevisaoPost(Long id) {
        return postRepository.findLastChangeRevision(id)
                .map(r -> converter(r, PostSnapshot::de))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nenhum histórico encontrado para o Post com id: " + id));
    }

    // ── Linha do tempo global ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaginaDTO<RevisaoResumoDTO> linhaDoTempo(Pageable pageable) {
        return PaginaDTO.de(revisaoAuditoriaRepository.findAllByOrderByRevDesc(pageable),
                RevisaoResumoDTO::de);
    }

    /** Tudo o que um autor alterou — a pergunta clássica de uma auditoria. */
    @Transactional(readOnly = true)
    public PaginaDTO<RevisaoResumoDTO> revisoesPorAutor(String autor, Pageable pageable) {
        return PaginaDTO.de(revisaoAuditoriaRepository.buscarPorAutor(autor, pageable),
                RevisaoResumoDTO::de);
    }

    // ── Interno ────────────────────────────────────────────────────────────

    private <E, T> PaginaDTO<RevisaoDTO<T>> historico(RevisionRepository<E, Long, Integer> repository,
                                                      Long id,
                                                      Pageable pageable,
                                                      Function<? super E, T> mapeador,
                                                      String entidade) {
        Page<Revision<Integer, E>> revisoes = repository.findRevisions(id, pageable);
        if (revisoes.getTotalElements() == 0) {
            // Sem nenhuma revisão o registro nunca existiu: um registro excluído
            // conservaria ao menos as revisões de criação e exclusão.
            throw new ResourceNotFoundException(
                    "Nenhum histórico encontrado para %s com id: %d".formatted(entidade, id));
        }
        return PaginaDTO.de(revisoes, r -> converter(r, mapeador));
    }

    private <E, T> RevisaoDTO<T> revisao(RevisionRepository<E, Long, Integer> repository,
                                         Long id,
                                         Integer numeroRevisao,
                                         Function<? super E, T> mapeador,
                                         String entidade) {
        return repository.findRevision(id, numeroRevisao)
                .map(r -> converter(r, mapeador))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Revisão %d não encontrada para %s com id: %d".formatted(numeroRevisao, entidade, id)));
    }

    /**
     * Converte uma {@code Revision} do Spring Data no DTO da API.
     *
     * <p>{@code getMetadata().getDelegate()} devolve a entidade de revisão desta
     * aplicação — é por ela que chegam autor e origem, os campos que o
     * {@code REVINFO} padrão do Envers não teria.
     */
    private <E, T> RevisaoDTO<T> converter(Revision<Integer, E> revision, Function<? super E, T> mapeador) {
        RevisaoAuditoria metadados = revision.getMetadata().getDelegate();
        TipoAlteracao tipo = TipoAlteracao.de(revision.getMetadata().getRevisionType());

        return new RevisaoDTO<>(
                metadados.getRev(),
                tipo,
                tipo.getDescricao(),
                metadados.getDataHora(),
                metadados.getAutor(),
                metadados.getOrigem(),
                mapeador.apply(revision.getEntity())
        );
    }
}
