package com.andre.infnethub.notificacao.service;

import com.andre.infnethub.notificacao.model.Destinatario;
import com.andre.infnethub.notificacao.repository.DestinatarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Quem existe e pode ser avisado — mantido pelos eventos de usuário.
 *
 * <p>Este serviço consome em paralelo, então os fatos sobre um mesmo usuário
 * podem ser processados fora de ordem. As duas defesas: a versão (o estado mais
 * velho não sobrescreve o mais novo) e a lápide (quem foi removido não volta).
 */
@Service
@RequiredArgsConstructor
public class CadastroDeDestinatarios {

    public static final String PAPEL_ALUNO = "ALUNO";

    private final DestinatarioRepository repositorio;

    /**
     * @return {@code true} se, depois de aplicado, o destinatário está ativo —
     *         {@code false} se é uma lápide, e o fato chegou tarde demais.
     */
    @Transactional
    public boolean registrar(Long usuarioId, String papel, long versao) {
        Destinatario destinatario = repositorio.findById(usuarioId)
                .map(existente -> {
                    existente.atualizar(papel, versao);
                    return existente;
                })
                .orElseGet(() -> repositorio.save(new Destinatario(usuarioId, papel, versao)));
        return !destinatario.isRemovido();
    }

    @Transactional
    public void remover(Long usuarioId) {
        repositorio.findById(usuarioId).ifPresentOrElse(
                Destinatario::remover,
                () -> repositorio.save(Destinatario.lapide(usuarioId)));
    }

    @Transactional(readOnly = true)
    public List<Long> alunosAtivos() {
        return repositorio.findByPapelAndRemovidoFalse(PAPEL_ALUNO).stream()
                .map(Destinatario::getUsuarioId)
                .toList();
    }
}
