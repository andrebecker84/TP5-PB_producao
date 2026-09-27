package com.andre.infnethub.boletim.mensageria;

import com.andre.infnethub.contratos.Canais;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.ConnectionFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * O lado consumidor da topologia: a fila do boletim e o caminho das mensagens
 * que ele não consegue processar.
 *
 * <pre>
 *   infnethub.eventos (topic) ──usuario.*──▶ boletim.usuarios ──falhou 3x──▶ infnethub.dlx (direct)
 *                                                                               │
 *                                                                               ▼
 *                                                                      boletim.usuarios.dlq
 * </pre>
 *
 * <p>Tudo declarado como bean: na primeira conexão, o Spring AMQP cria no
 * RabbitMQ o que ainda não existir. Subir o boletim num broker vazio é o
 * bastante para a topologia aparecer no painel.
 *
 * <p><strong>Atenção ao mudar argumentos de fila.</strong> O RabbitMQ não altera
 * uma fila existente: redeclarar {@code boletim.usuarios} com outro argumento
 * falha com {@code PRECONDITION_FAILED}. Em desenvolvimento, apague a fila no
 * painel antes de subir a versão nova.
 */
@Configuration
// O relay da caixa de saída roda por agendamento fixo — sem isto, ele nunca é
// chamado e as respostas da saga ficam paradas na tabela.
@EnableScheduling
class MensageriaConfig {

    /**
     * Quantas vezes o RabbitMQ reentrega a mesma mensagem antes de desistir
     * dela. É a proteção de último caso: as tentativas normais são feitas pelo
     * Spring dentro do consumidor (ver {@code application.properties}), e este
     * limite só entra em jogo se o próprio serviço cair repetidamente no meio
     * do processamento — o caso em que a mensagem derrubaria o consumidor em
     * laço para sempre.
     */
    private static final int LIMITE_DE_ENTREGAS = 5;

    @Bean
    TopicExchange exchangeDeEventos() {
        return ExchangeBuilder.topicExchange(Canais.EXCHANGE_EVENTOS).durable(true).build();
    }

    @Bean
    DirectExchange exchangeDeMensagensMortas() {
        return ExchangeBuilder.directExchange(Canais.EXCHANGE_MORTAS).durable(true).build();
    }

    /**
     * Fila <em>quorum</em>, e não clássica.
     *
     * <p>É o tipo recomendado pelo RabbitMQ para dados que não podem se perder:
     * replicada por consenso (Raft) entre os nós do cluster, com contagem de
     * entregas embutida. As filas clássicas espelhadas, a alternativa antiga,
     * foram removidas no RabbitMQ 4. Com um nó só, como aqui, a replicação não
     * acontece — mas a fila já nasce do tipo certo para o dia em que houver três.
     */
    @Bean
    Queue filaDeUsuarios() {
        return QueueBuilder.durable(Canais.FILA_BOLETIM_USUARIOS)
                .quorum()
                .deliveryLimit(LIMITE_DE_ENTREGAS)
                .deadLetterExchange(Canais.EXCHANGE_MORTAS)
                .deadLetterRoutingKey(Canais.dlqDe(Canais.FILA_BOLETIM_USUARIOS))
                .build();
    }

    @Bean
    Queue filaDeUsuariosMortos() {
        return QueueBuilder.durable(Canais.dlqDe(Canais.FILA_BOLETIM_USUARIOS)).quorum().build();
    }

    /** Tudo sobre usuários, e só sobre usuários. */
    @Bean
    Binding ligacaoUsuarios() {
        return BindingBuilder.bind(filaDeUsuarios()).to(exchangeDeEventos()).with(Canais.PADRAO_USUARIO);
    }

    @Bean
    Binding ligacaoUsuariosMortos() {
        return BindingBuilder.bind(filaDeUsuariosMortos())
                .to(exchangeDeMensagensMortas())
                .with(Canais.dlqDe(Canais.FILA_BOLETIM_USUARIOS));
    }

    // ── Perguntas que esperam resposta ─────────────────────────────────────

    @Bean
    DirectExchange exchangeDeConsultas() {
        return ExchangeBuilder.directExchange(Canais.EXCHANGE_CONSULTAS).durable(true).build();
    }

    /**
     * A fila das perguntas: com validade, sem fila de mensagens mortas.
     *
     * <p>É o oposto da {@code boletim.usuarios} em quase tudo, e cada diferença
     * vem da natureza do que trafega. Um evento perdido é um dado que nunca
     * mais volta, então ele espera indefinidamente e, se falhar, é guardado.
     * Uma pergunta cujo dono já desistiu de esperar não tem valor nenhum:
     * respondê-la seria escrever numa fila de resposta que já foi embora.
     * Guardá-la numa DLQ seria acumular perguntas que ninguém vai reler.
     *
     * <p>Daí o {@code x-message-ttl}: passado o prazo, o próprio broker a
     * descarta. A fila se limpa sozinha quando o core fica fora do ar no meio
     * de uma conversa.
     */
    @Bean
    Queue filaDeConsultas() {
        return QueueBuilder.durable(Canais.FILA_BOLETIM_CONSULTAS)
                .quorum()
                .ttl(Canais.VALIDADE_DA_PERGUNTA_MS)
                .build();
    }

    @Bean
    Binding ligacaoConsultas() {
        return BindingBuilder.bind(filaDeConsultas())
                .to(exchangeDeConsultas())
                .with(Canais.ROTA_SITUACAO_ACADEMICA);
    }

    /**
     * Desistir depressa de um nó que não responde.
     *
     * <p>Os padrões do cliente AMQP são generosos — um minuto para abrir a
     * conexão TCP, dez segundos para o aperto de mãos —, e com três endereços
     * na lista, uma tentativa contra um cluster em desligamento chegou a
     * prender a thread do agendador por 36 segundos, medidos. Enquanto isso,
     * nem a caixa de saída registrava a falha, nem o prazo das sagas era
     * verificado. Com três segundos por etapa, o nó que não responde é trocado
     * pelo seguinte, e o broker inteiro fora do ar vira falha registrada em
     * poucos segundos. A abertura da conexão TCP tem o mesmo limite, em
     * spring.rabbitmq.connection-timeout.
     */
    @Bean
    ConnectionFactoryCustomizer desistirDepressa() {
        return fabrica -> fabrica.setHandshakeTimeout(3_000);
    }

    /**
     * JSON, com lista fechada de pacotes aceitos.
     *
     * <p>O conversor escolhe a classe a instanciar a partir de um cabeçalho que
     * vem <em>de fora</em>. Sem restrição, quem conseguisse publicar na fila
     * escolheria a classe a ser carregada — o caminho clássico da
     * desserialização insegura. Por aqui só chegam eventos de usuário e
     * perguntas sobre alunos, então são só esses dois pacotes que se liberam.
     */
    @Bean
    MessageConverter conversorJson() {
        return new JacksonJsonMessageConverter(
                "com.andre.infnethub.contratos.usuario",
                "com.andre.infnethub.contratos.consulta",
                "com.andre.infnethub.contratos.expurgo");
    }
}
