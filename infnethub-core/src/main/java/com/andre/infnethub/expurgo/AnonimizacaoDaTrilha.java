package com.andre.infnethub.expurgo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tira a identificação de uma pessoa da trilha de auditoria, sem apagar a trilha.
 *
 * <h2>Por que a anonimização do cadastro não basta</h2>
 * <p>Anonimizar a linha de {@code usuarios} cria uma revisão nova no Envers — e
 * só. As revisões anteriores continuam guardando o nome e o e-mail de antes, e
 * reconstituir a pessoa a partir delas é uma consulta. Além disso, o que ela
 * fez no sistema carrega a identificação em mais dois lugares: o autor de cada
 * revisão ({@code revisao_auditoria.autor}) e as colunas
 * {@code criado_por}/{@code atualizado_por} de tudo o que ela criou ou alterou.
 *
 * <h2>O que se preserva</h2>
 * <p>A trilha continua respondendo "o que aconteceu, quando e em que ordem", e
 * continua ligando cada ato ao mesmo identificador — {@code [usuario:ID]}, que
 * já não leva a ninguém. O que desaparece é só a resposta a "quem era essa
 * pessoa". É a distinção do art. 12 da LGPD: dado anonimizado não é dado
 * pessoal, e manter o registro dos atos não exige manter a identidade.
 *
 * <h2>Por que SQL, e não o Envers</h2>
 * <p>Revisão do Envers é, por definição, imutável pela API: não há como
 * reescrever o passado por ela. Aqui é exatamente isso que se quer, e de forma
 * restrita — só colunas de identificação, só da pessoa em questão.
 *
 * <p>{@link Propagation#MANDATORY}: roda dentro da transação que conclui a
 * saga. Se a trilha fosse anonimizada numa transação própria e a conclusão
 * falhasse depois, restaria uma pessoa ativa com o histórico apagado.
 */
@Component
public class AnonimizacaoDaTrilha {

    /** Tabelas com {@code criado_por} e {@code atualizado_por} (V1). */
    private static final String[] TABELAS_COM_AUTORIA =
            {"usuarios", "posts", "vagas", "comentarios", "curtidas"};

    @PersistenceContext
    private EntityManager em;

    /** O que substitui a identificação por extenso, mantendo o vínculo pelo id. */
    public static String autorAnonimo(Long usuarioId) {
        return "Usuário removido [usuario:%d]".formatted(usuarioId);
    }

    /**
     * @return quantas linhas foram alteradas, para o registro do processo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int anonimizar(Long usuarioId) {
        int alteradas = em.createNativeQuery("""
                UPDATE usuarios_aud
                   SET nome = 'Usuário removido',
                       email = :email,
                       escola = NULL,
                       ultimo_bloco = NULL,
                       classe = NULL
                 WHERE id = :id
                """)
                .setParameter("email", "removido+" + usuarioId + "@infnethub.invalid")
                .setParameter("id", usuarioId)
                .executeUpdate();

        // O autor é gravado como "Nome <email> [usuario:ID]" pelo filtro de
        // auditoria; o sufixo é a âncora. A origem (endereço IP) também é dado
        // pessoal quando ligada a uma pessoa, e sai junto.
        String marca = "%[usuario:" + usuarioId + "]";
        String anonimo = autorAnonimo(usuarioId);

        alteradas += em.createNativeQuery("""
                UPDATE revisao_auditoria
                   SET autor = :anonimo, origem = NULL
                 WHERE autor LIKE :marca
                """)
                .setParameter("anonimo", anonimo)
                .setParameter("marca", marca)
                .executeUpdate();

        for (String tabela : TABELAS_COM_AUTORIA) {
            alteradas += em.createNativeQuery(
                            "UPDATE " + tabela + " SET criado_por = :anonimo WHERE criado_por LIKE :marca")
                    .setParameter("anonimo", anonimo)
                    .setParameter("marca", marca)
                    .executeUpdate();
            alteradas += em.createNativeQuery(
                            "UPDATE " + tabela + " SET atualizado_por = :anonimo WHERE atualizado_por LIKE :marca")
                    .setParameter("anonimo", anonimo)
                    .setParameter("marca", marca)
                    .executeUpdate();
        }
        return alteradas;
    }
}
