package com.andre.infnethub.gateway.borda;

import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.netty.http.Http11SslContextSpec;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * Como o gateway confere o TLS dos serviços para onde encaminha as requisições.
 *
 * <h2>Cadeia, sim; nome, não — e só neste trecho</h2>
 * <p>Os serviços se registram no Eureka pelo IP de cada instância. É o que faz
 * o balanceamento enxergar as três instâncias do serviço de notificação como
 * três, e parar de mandar requisições para a que sair. Registrar pelo nome foi
 * tentado, e falhou de um jeito instrutivo: todas as instâncias ficavam com o
 * mesmo nome, e o cache de DNS do gateway continuou mandando requisições ao IP
 * de um contêiner já recriado por mais de dois minutos — 500 em toda chamada.
 *
 * <p>O certificado dos serviços, porém, não tem os IPs do Docker, que mudam a
 * cada subida. A verificação de nome falharia sempre. O que continua sendo
 * verificado, integralmente, é a <strong>cadeia</strong>: o certificado
 * apresentado tem de ter sido emitido pela autoridade de desenvolvimento, na
 * qual a JVM confia — validade, assinatura, uso de chave, tudo pelo
 * gerenciador de confiança padrão da JVM.
 *
 * <p>Por que isso basta aqui: essa autoridade emitiu um único certificado — o
 * dos serviços — e a chave dela é apagada logo depois, na geração (ver
 * {@code infra/certificados/gerar.sh}). Quem apresenta uma cadeia válida tem,
 * necessariamente, a chave dos serviços. Com uma identidade só, conferir o nome
 * não distinguiria ninguém. Os demais trechos — serviços → Eureka, Keycloak,
 * RabbitMQ, PostgreSQL; front-end → gateway; Prometheus → alvos — seguem
 * conferindo o nome também.
 *
 * <h2>Por que um gerenciador de confiança, e não um parâmetro</h2>
 * <p>O Reactor Netty religa a conferência de nome em todo cliente HTTP, por
 * cima do que a configuração do contexto TLS diga. Em vez de disputar a ordem
 * dessas configurações, a decisão fica no único lugar que a toma de fato: o
 * gerenciador de confiança, que aqui confere a cadeia pela variante que não
 * compara o nome.
 *
 * <p>Só no perfil {@code tls}: fora do Compose os serviços falam HTTP simples,
 * e não há o que configurar.
 */
@Configuration(proxyBeanMethods = false)
@Profile("tls")
class ConexaoComOsServicos {

    @Bean
    HttpClientCustomizer cadeiaConferidaSemNome() throws GeneralSecurityException {
        X509ExtendedTrustManager cadeia = new CadeiaSemNome(padraoDaJvm());
        return cliente -> cliente.secure(tls -> tls
                .sslContext(Http11SslContextSpec.forClient()
                        .configure(contexto -> contexto.trustManager(cadeia))));
    }

    /** O gerenciador de confiança da JVM — o da autoridade de desenvolvimento. */
    static X509TrustManager padraoDaJvm() throws GeneralSecurityException {
        TrustManagerFactory fabrica = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        fabrica.init((KeyStore) null);
        return Arrays.stream(fabrica.getTrustManagers())
                .filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast)
                .findFirst()
                .orElseThrow(() -> new GeneralSecurityException("a JVM não tem gerenciador de confiança X.509"));
    }

    /**
     * Confere a cadeia pelo gerenciador padrão e não compara o nome.
     *
     * <p>As variantes com {@link SSLEngine} e {@link Socket} são as que o JDK
     * usa para conferir também o nome; aqui todas levam à variante que só
     * confere a cadeia.
     */
    static final class CadeiaSemNome extends X509ExtendedTrustManager {

        private final X509TrustManager padrao;

        CadeiaSemNome(X509TrustManager padrao) {
            this.padrao = padrao;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] cadeia, String tipo) throws CertificateException {
            padrao.checkServerTrusted(cadeia, tipo);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] cadeia, String tipo, SSLEngine motor)
                throws CertificateException {
            padrao.checkServerTrusted(cadeia, tipo);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] cadeia, String tipo, Socket soquete)
                throws CertificateException {
            padrao.checkServerTrusted(cadeia, tipo);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] cadeia, String tipo) throws CertificateException {
            padrao.checkClientTrusted(cadeia, tipo);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] cadeia, String tipo, SSLEngine motor)
                throws CertificateException {
            padrao.checkClientTrusted(cadeia, tipo);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] cadeia, String tipo, Socket soquete)
                throws CertificateException {
            padrao.checkClientTrusted(cadeia, tipo);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return padrao.getAcceptedIssuers();
        }
    }
}
