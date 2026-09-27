package com.andre.infnethub.contratos.expurgo;

import com.andre.infnethub.contratos.EventoDeIntegracao;

import java.time.Instant;
import java.util.UUID;

/**
 * O boletim cumpriu a sua parte: a identidade do aluno não está mais lá.
 *
 * <h2>Anonimizar, e não apagar</h2>
 * <p>O histórico acadêmico — matrículas, conceitos, carga horária — permanece.
 * Não é resistência ao direito de eliminação: é a outra obrigação legal, a de
 * manter o registro escolar. O que sai é o vínculo com a pessoa, e a LGPD
 * reconhece o dado anonimizado como fora do seu alcance (art. 12).
 *
 * <p>Por isso o campo abaixo: quem recebe a confirmação precisa saber o que
 * ficou, para poder responder ao titular o que foi feito com os dados dele.
 *
 * @param processoId o processo a que esta resposta pertence — copiado do
 *                   pedido, e é por ele, e não pela pessoa, que o core a casa.
 * @param registrosMantidos quantas matrículas continuam no boletim, agora sem
 *                          identificação de quem as cursou.
 */
public record AlunoAnonimizadoV1(
        UUID mensagemId,
        Instant ocorridoEm,
        Long processoId,
        Long usuarioId,
        int registrosMantidos
) implements EventoDeIntegracao {

    @Override
    public String chave() {
        return String.valueOf(usuarioId);
    }
}
