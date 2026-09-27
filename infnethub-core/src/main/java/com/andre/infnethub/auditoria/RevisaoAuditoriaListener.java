package com.andre.infnethub.auditoria;

import org.hibernate.envers.RevisionListener;

/**
 * Carimba autor e origem em cada revisão criada pelo Envers.
 *
 * <p>Chamado uma única vez por transação que altere alguma entidade anotada com
 * {@code @Audited}, imediatamente antes da revisão ser gravada.
 */
public class RevisaoAuditoriaListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        RevisaoAuditoria revisao = (RevisaoAuditoria) revisionEntity;
        revisao.setAutor(ContextoAuditoria.autorAtual());
        revisao.setOrigem(ContextoAuditoria.origemAtual());
    }
}
