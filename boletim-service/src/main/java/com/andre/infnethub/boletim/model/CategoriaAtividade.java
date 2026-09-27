package com.andre.infnethub.boletim.model;

/**
 * Categorias de atividade fora das disciplinas dos blocos, com a carga horária
 * que o curso exige de cada uma para a integralização.
 *
 * <p>A eletiva é a única que reprova por frequência — as demais contam pelo
 * cumprimento da carga. Por isso {@code exigePresenca} é atributo da categoria:
 * é regra do curso, não característica de uma atividade específica.
 *
 * <p>A eletiva também é a única sem meta própria: ela integra a carga das
 * disciplinas, e não um total separado. Daí a meta zero.
 */
public enum CategoriaAtividade {

    EXTENSAO("Projetos Supervisionados de Extensão", 400, false),
    ELETIVA("Disciplinas Eletivas", 0, true),
    ESTAGIO("Estágio Obrigatório", 400, false),
    COMPLEMENTAR("Atividades Complementares", 140, false);

    private final String descricao;
    private final int cargaHorariaExigida;
    private final boolean exigePresenca;

    CategoriaAtividade(String descricao, int cargaHorariaExigida, boolean exigePresenca) {
        this.descricao = descricao;
        this.cargaHorariaExigida = cargaHorariaExigida;
        this.exigePresenca = exigePresenca;
    }

    public String getDescricao() { return descricao; }

    public int getCargaHorariaExigida() { return cargaHorariaExigida; }

    public boolean isExigePresenca() { return exigePresenca; }
}
