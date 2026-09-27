package com.andre.infnethub.contratos.consulta;

/**
 * "Este aluno tem vida acadêmica no boletim?"
 *
 * <p>Não é evento nem comando, e por isso não implementa
 * {@link com.andre.infnethub.contratos.MensagemDeIntegracao}: não é um fato
 * anunciado, não é um pedido a executar, e — o que muda tudo — <strong>não
 * precisa sobreviver a nada</strong>. Uma pergunta cuja resposta chegue amanhã
 * não vale nada; quem perguntou já respondeu ao usuário e foi embora. Por isso
 * não passa pela caixa de saída, não tem {@code messageId} a deduplicar e não
 * tem fila de mensagens mortas.
 *
 * <h2>Por que existe, se o TP4 é sobre desacoplar</h2>
 * <p>Porque nem tudo deve ser desacoplado, e dizer isso faz parte de avaliar a
 * arquitetura com honestidade. A secretaria, antes de excluir uma pessoa,
 * precisa saber <em>agora</em> se há matrícula em andamento — e essa resposta
 * não tolera atraso nem valor antigo. Replicar o boletim inteiro dentro do core
 * para responder a uma tela seria replicar dado sensível para sempre por causa
 * de uma pergunta ocasional: o custo do desacoplamento passaria o benefício.
 *
 * <p>O acoplamento temporal que isto reintroduz é real e está contido: só esta
 * rota o tem, o tempo de espera é curto e explícito, e a resposta diz quando
 * não conseguiu falar com o boletim, em vez de fingir que o aluno não tem nada.
 */
public record SituacaoAcademicaPerguntaV1(Long alunoId) {
}
