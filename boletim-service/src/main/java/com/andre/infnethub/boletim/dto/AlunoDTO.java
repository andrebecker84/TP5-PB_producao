package com.andre.infnethub.boletim.dto;

import com.andre.infnethub.boletim.aluno.AlunoReplica;

/**
 * Os dados de identificação do aluno, como o boletim os conhece.
 *
 * <p>Até o TP3 este record era a resposta de uma chamada ao infnethub-core,
 * feita a cada boletim aberto. No TP4 ele sai da réplica local, alimentada pelos
 * eventos que o core publica. A forma continua a mesma — o front-end não
 * precisou mudar —, mas o significado de {@link #isIndisponivel()} mudou: antes
 * era "o core não respondeu agora"; hoje é "o cadastro deste aluno ainda não
 * chegou até aqui".
 *
 * <p>É deliberadamente menor que o {@code Usuario} do core. E-mail e datas de
 * criação não interessam a um boletim — e, como o evento nem os transporta,
 * este serviço não guarda dado pessoal de que não precisa.
 */
public record AlunoDTO(
        Long id,
        String nome,
        String escola,
        String ultimoBloco,
        String classe,
        String papel,
        String papelDescricao
) {

    public static AlunoDTO de(AlunoReplica replica) {
        return new AlunoDTO(replica.getId(), replica.getNome(), replica.getEscola(),
                replica.getUltimoBloco(), replica.getClasse(), replica.getPapel(), replica.getPapelDescricao());
    }

    /**
     * O aluno cujo cadastro ainda não foi recebido.
     *
     * <p>O {@code id} é verdadeiro — foi o cliente quem o informou —, e só o que
     * dependia do evento vem vazio. A interface distingue este caso e avisa, em
     * vez de exibir um nome inventado.
     */
    public static AlunoDTO indisponivel(Long alunoId) {
        return new AlunoDTO(alunoId, null, null, null, null, null, null);
    }

    public boolean isIndisponivel() {
        return nome == null;
    }
}
