package com.andre.infnethub.auditoria;

import java.util.Objects;

/**
 * Guarda o autor e a origem da alteração em curso.
 *
 * <p>O {@link RevisaoAuditoriaListener} é instanciado pelo Hibernate, fora do
 * contêiner do Spring, e por isso não recebe injeção de dependências. O estado
 * viaja então por {@link ThreadLocal}, preenchido pelo
 * {@link ContextoAuditoriaFilter} no início da requisição e sempre liberado ao
 * final dela — sem o {@link #limpar()} o valor vazaria para a próxima
 * requisição atendida pela mesma thread do pool.
 */
public final class ContextoAuditoria {

    /** Alteração sem requisição HTTP associada (carga inicial, tarefas internas). */
    public static final String AUTOR_SISTEMA = "sistema";

    /** Requisição que não identificou o usuário no cabeçalho {@code X-Usuario-Id}. */
    public static final String AUTOR_ANONIMO = "anonimo";

    public static final String ORIGEM_INTERNA = "interno";

    private static final ThreadLocal<String> AUTOR  = new ThreadLocal<>();
    private static final ThreadLocal<String> ORIGEM = new ThreadLocal<>();

    private ContextoAuditoria() {
    }

    public static void definirAutor(String autor) {
        AUTOR.set(autor);
    }

    public static void definirOrigem(String origem) {
        ORIGEM.set(origem);
    }

    public static String autorAtual() {
        return Objects.requireNonNullElse(AUTOR.get(), AUTOR_SISTEMA);
    }

    public static String origemAtual() {
        return Objects.requireNonNullElse(ORIGEM.get(), ORIGEM_INTERNA);
    }

    public static void limpar() {
        AUTOR.remove();
        ORIGEM.remove();
    }
}
