package com.andre.infnethub.model;

/** Bounded Context: Identidade — papel do usuário na plataforma */
public enum Papel {
    ALUNO("Aluno(a)"),
    PROFESSOR("Professor(a)"),
    SECRETARIA("Secretaria"),
    COORDENADOR("Coordenador(a)"),
    /**
     * Quem opera a infraestrutura da plataforma — filas, métricas, saúde dos
     * serviços. Não é papel acadêmico: não publica vaga, não lê boletim, não
     * administra cadastro. O que ele tem, e os outros não, é o acesso ao painel
     * de operação (Grafana).
     */
    SUPORTE_TI("Suporte de TI");

    private final String descricao;

    Papel(String descricao) { this.descricao = descricao; }

    public String getDescricao() { return descricao; }
}
