package com.andre.infnethub.mensageria;

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
 * O lado produtor da topologia.
 *
 * <p>Para o que publica, o core declara só a exchange. As filas pertencem a
 * quem consome, e cada consumidor declara a sua: o core não sabe — nem deveria
 * saber — quem está ouvindo. É esse desconhecimento que deixa acrescentar um
 * consumidor novo sem tocar numa linha deste serviço.
 *
 * <p>A exceção é {@code core.expurgo}, declarada aqui porque é <em>deste</em>
 * serviço. Na saga de expurgo o core deixa de ser só produtor e passa a ouvir
 * as respostas dos participantes — e continua valendo a mesma regra: a fila
 * pertence a quem consome.
 *
 * <p>Os dois lados declaram a mesma exchange com os mesmos atributos. A
 * declaração é idempotente no RabbitMQ, e assim nenhum dos dois depende de o
 * outro ter subido primeiro.
 */
@Configuration
@EnableScheduling
class MensageriaConfig {

    /** Quantas reentregas antes de a mensagem ir para a fila de mensagens mortas. */
    private static final int LIMITE_DE_ENTREGAS = 5;

    @Bean
    TopicExchange exchangeDeEventos() {
        return ExchangeBuilder.topicExchange(Canais.EXCHANGE_EVENTOS).durable(true).build();
    }

    /**
     * A exchange dos pedidos. O core publica comandos nela, e é o serviço de
     * notificação que declara a fila do outro lado — o mesmo desconhecimento
     * mútuo dos eventos, embora aqui o destinatário seja um só.
     */
    @Bean
    DirectExchange exchangeDeComandos() {
        return ExchangeBuilder.directExchange(Canais.EXCHANGE_COMANDOS).durable(true).build();
    }

    /** A exchange das perguntas — ver {@link ConsultaAoBoletim}. */
    @Bean
    DirectExchange exchangeDeConsultas() {
        return ExchangeBuilder.directExchange(Canais.EXCHANGE_CONSULTAS).durable(true).build();
    }

    // ── O core como consumidor: as respostas da saga de expurgo ────────────

    @Bean
    DirectExchange exchangeDeMensagensMortas() {
        return ExchangeBuilder.directExchange(Canais.EXCHANGE_MORTAS).durable(true).build();
    }

    /**
     * A fila que faz do core também um consumidor.
     *
     * <p>Consumidor <strong>único</strong> — sem {@code concurrency}. As
     * respostas da saga mudam o estado de um processo, e duas threads
     * concluindo e revertendo o mesmo expurgo ao mesmo tempo produziriam um
     * resultado que depende de quem chegou primeiro. O volume é o de pedidos de
     * remoção de uma instituição: não é aqui que a escala se decide.
     */
    @Bean
    Queue filaDoExpurgo() {
        return QueueBuilder.durable(Canais.FILA_CORE_EXPURGO)
                .quorum()
                .deliveryLimit(LIMITE_DE_ENTREGAS)
                .deadLetterExchange(Canais.EXCHANGE_MORTAS)
                .deadLetterRoutingKey(Canais.dlqDe(Canais.FILA_CORE_EXPURGO))
                .build();
    }

    @Bean
    Queue filaDoExpurgoMortas() {
        return QueueBuilder.durable(Canais.dlqDe(Canais.FILA_CORE_EXPURGO)).quorum().build();
    }

    @Bean
    Binding ligacaoDoExpurgo() {
        return BindingBuilder.bind(filaDoExpurgo()).to(exchangeDeEventos()).with(Canais.PADRAO_ALUNO);
    }

    @Bean
    Binding ligacaoDoExpurgoMortas() {
        return BindingBuilder.bind(filaDoExpurgoMortas())
                .to(exchangeDeMensagensMortas())
                .with(Canais.dlqDe(Canais.FILA_CORE_EXPURGO));
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
     * JSON no corpo, e não serialização Java.
     *
     * <p>A serialização binária da JVM amarraria o consumidor à mesma linguagem
     * e à mesma versão da classe. Em JSON, qualquer serviço — em qualquer
     * linguagem — lê o evento, e o painel do RabbitMQ mostra o corpo legível.
     * O Spring Boot liga este conversor ao {@code RabbitTemplate} sozinho, por
     * ser o único {@link MessageConverter} do contexto.
     *
     * <p>A lista de pacotes confiáveis vale só para o que o core <em>lê</em>:
     * as respostas das consultas e as da saga de expurgo. Ele publica de tudo,
     * e serializar não passa por esta lista. Restringir a leitura custa nada e
     * fecha a porta clássica da desserialização insegura, em que o cabeçalho da
     * mensagem escolhe a classe a carregar.
     */
    @Bean
    MessageConverter conversorJson() {
        return new JacksonJsonMessageConverter(
                "com.andre.infnethub.contratos.consulta",
                "com.andre.infnethub.contratos.expurgo");
    }
}
