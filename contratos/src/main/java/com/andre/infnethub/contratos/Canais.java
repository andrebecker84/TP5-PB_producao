package com.andre.infnethub.contratos;

/**
 * Os endereços do broker: exchanges, chaves de roteamento e filas.
 *
 * <p>Centralizados aqui porque um erro de digitação numa chave de roteamento não
 * falha em lugar nenhum: o RabbitMQ aceita a publicação, não encontra fila
 * ligada àquela chave e descarta a mensagem em silêncio. Com as constantes num
 * módulo compartilhado, produtor e consumidor escrevem o mesmo texto porque
 * leem a mesma linha.
 *
 * <h2>Convenção das chaves</h2>
 * <p>{@code <agregado>.<fato no particípio>} — {@code usuario.cadastrado},
 * {@code usuario.removido}. A exchange é do tipo {@code topic}, então quem
 * consome escolhe o recorte pelo padrão da ligação: {@code usuario.*} recebe
 * tudo sobre usuários, {@code *.removido} recebe toda remoção, de qualquer
 * agregado.
 *
 * <h2>Convenção das filas</h2>
 * <p>{@code <serviço consumidor>.<assunto>}. A fila pertence a quem consome, não
 * a quem publica: o core anuncia {@code usuario.cadastrado} uma vez, e cada
 * interessado tem a sua fila, com seu próprio ritmo e sua própria fila de
 * mensagens mortas. Um consumidor lento ou fora do ar não atrasa os outros.
 */
public final class Canais {

    /** Eventos de domínio de todos os serviços. Tipo {@code topic}. */
    public static final String EXCHANGE_EVENTOS = "infnethub.eventos";

    /**
     * Comandos endereçados a um serviço. Tipo {@code direct}: a chave é o
     * endereço do executor, não um assunto a assinar.
     *
     * <p>A escolha do tipo é a diferença inteira entre os dois padrões. Numa
     * {@code topic}, acrescentar um consumidor com a mesma ligação faz a
     * mensagem ser entregue <em>duas vezes</em>, a dois serviços — o que é
     * exatamente o que se quer de um evento e exatamente o que arruinaria um
     * comando, que seria executado em dobro.
     */
    public static final String EXCHANGE_COMANDOS = "infnethub.comandos";

    /**
     * Perguntas que esperam resposta — o padrão <em>request/reply</em>. Também
     * {@code direct}: uma pergunta tem um respondente.
     *
     * <p>Fica separada dos comandos de propósito. Comando e pergunta parecem
     * iguais no desenho — chave, fila, um consumidor —, mas têm exigências
     * opostas: o comando <strong>não pode se perder</strong> e espera na fila o
     * tempo que for; a pergunta <strong>não pode demorar</strong> e perde a
     * validade em segundos. Misturá-los na mesma exchange convidaria a dar à
     * pergunta as garantias caras do comando, ou ao comando a pressa da
     * pergunta.
     */
    public static final String EXCHANGE_CONSULTAS = "infnethub.consultas";

    /**
     * Destino das mensagens que esgotaram as tentativas. Tipo {@code direct}:
     * cada fila de mensagens mortas é ligada pelo próprio nome.
     */
    public static final String EXCHANGE_MORTAS = "infnethub.dlx";

    public static final String ROTA_USUARIO_CADASTRADO = "usuario.cadastrado";
    public static final String ROTA_USUARIO_ATUALIZADO = "usuario.atualizado";
    public static final String ROTA_USUARIO_REMOVIDO = "usuario.removido";

    /**
     * Abertura da saga de expurgo. Casa com {@link #PADRAO_USUARIO}, então
     * chega a quem já escuta os fatos sobre usuários — sem ligação nova.
     */
    public static final String ROTA_EXPURGO_SOLICITADO = "usuario.expurgo-solicitado";

    /** Respostas dos participantes da saga, publicadas pelo boletim-service. */
    public static final String ROTA_ALUNO_ANONIMIZADO = "aluno.anonimizado";
    public static final String ROTA_ANONIMIZACAO_RECUSADA = "aluno.anonimizacao-recusada";

    public static final String ROTA_POST_CURTIDO = "post.curtido";
    public static final String ROTA_POST_COMENTADO = "post.comentado";
    public static final String ROTA_VAGA_PUBLICADA = "vaga.publicada";

    /**
     * Endereço do executor de avisos: o serviço de notificação.
     *
     * <p>O mesmo endereço serve ao aviso imediato e ao agendado. O imediato vai
     * pela exchange de comandos; o agendado entra na {@link SalaDeEspera}, com
     * este endereço escrito no fim da chave, e sai dela aqui.
     */
    public static final String ROTA_ENVIAR_AVISO = "notificacao.enviar";

    /** Endereço de quem sabe responder sobre a vida acadêmica de um aluno. */
    public static final String ROTA_SITUACAO_ACADEMICA = "boletim.situacao";

    /** Padrão de ligação para todos os fatos sobre usuários. */
    public static final String PADRAO_USUARIO = "usuario.*";
    public static final String PADRAO_POST = "post.*";
    public static final String PADRAO_VAGA = "vaga.*";

    /**
     * Tudo o que o boletim anuncia sobre alunos — hoje, as respostas da saga de
     * expurgo. É por esta ligação que o core deixa de ser só produtor: na saga,
     * quem começou precisa ouvir a resposta para saber como terminar.
     */
    public static final String PADRAO_ALUNO = "aluno.*";

    /** Fila do boletim-service que mantém a réplica local dos alunos. */
    public static final String FILA_BOLETIM_USUARIOS = "boletim.usuarios";

    /**
     * Fila das perguntas dirigidas ao boletim.
     *
     * <p>Tem prazo de validade curto e nenhuma fila de mensagens mortas: uma
     * pergunta que passou do tempo de espera de quem perguntou não deve ser
     * respondida, nem guardada. Responder a ela seria escrever numa fila de
     * respostas que já não existe.
     */
    public static final String FILA_BOLETIM_CONSULTAS = "boletim.consultas";

    /** Quanto uma pergunta sobrevive na fila antes de deixar de fazer sentido. */
    public static final int VALIDADE_DA_PERGUNTA_MS = 10_000;

    /**
     * Fila do notificacao-service. Uma só para todos os assuntos que geram
     * notificação, consumida em paralelo por quantas instâncias houver
     * (<em>competing consumers</em>): aqui a ordem entre mensagens não importa,
     * e o volume — uma curtida vira uma notificação — é o que pede escala.
     */
    public static final String FILA_NOTIFICACAO_EVENTOS = "notificacao.eventos";

    /**
     * Fila do <em>core</em> — a que faz dele também um consumidor.
     *
     * <p>Existe por causa da saga: quem a inicia precisa saber como ela
     * terminou. Recebe as respostas dos participantes ({@code aluno.*}) e nada
     * mais; os fatos que o próprio core publica não voltam para ele.
     */
    public static final String FILA_CORE_EXPURGO = "core.expurgo";

    /** Fila de trabalho dos comandos de aviso. */
    public static final String FILA_NOTIFICACAO_COMANDOS = "notificacao.comandos";

    private Canais() {
    }

    /** Nome da fila de mensagens mortas de uma fila. */
    public static String dlqDe(String fila) {
        return fila + ".dlq";
    }
}
