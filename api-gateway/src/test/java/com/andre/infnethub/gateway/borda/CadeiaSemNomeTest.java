package com.andre.infnethub.gateway.borda;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O gerenciador de confiança do trecho gateway → serviços, com certificados de
 * verdade.
 *
 * <p>A decisão tem duas metades: o nome deixa de ser conferido — e a cadeia
 * continua sendo. A que importa para a segurança é a segunda, e é ela que este
 * teste prova: um certificado de fora é recusado mesmo com o nome certo. A
 * primeira metade só se manifesta num handshake de verdade (o JDK compara o
 * nome durante a negociação, não antes), e quem a prova é a coleção HTTP: ela
 * só passa se o gateway alcançar cada serviço pelo IP registrado no Eureka.
 *
 * <p>Os certificados são gerados pelo keytool do próprio JDK: uma autoridade,
 * um certificado emitido por ela para o nome "servico", e um certificado
 * autoassinado de fora, com o mesmo nome.
 */
@DisplayName("Trecho gateway → serviços — cadeia conferida, nome não")
class CadeiaSemNomeTest {

    private static final String SENHA = "senha-de-teste";

    @TempDir
    static Path pasta;

    private static X509TrustManager confiaNaAutoridade;
    private static X509Certificate[] emitidoPelaAutoridade;
    private static X509Certificate[] deFora;

    @BeforeAll
    static void gerarCertificados() throws Exception {
        keytool("-genkeypair", "-alias", "ca", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=autoridade-de-teste", "-ext", "bc:c", "-validity", "2",
                "-keystore", "ca.p12");
        keytool("-exportcert", "-rfc", "-alias", "ca", "-keystore", "ca.p12", "-file", "ca.crt");

        keytool("-genkeypair", "-alias", "servico", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=servico", "-validity", "2", "-keystore", "servico.p12");
        keytool("-certreq", "-alias", "servico", "-keystore", "servico.p12", "-file", "servico.csr");
        keytool("-gencert", "-alias", "ca", "-keystore", "ca.p12", "-infile", "servico.csr",
                "-outfile", "servico.crt", "-rfc", "-ext", "san=dns:servico", "-validity", "2");

        keytool("-genkeypair", "-alias", "impostor", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=servico", "-ext", "san=dns:servico", "-validity", "2",
                "-keystore", "impostor.p12");
        keytool("-exportcert", "-rfc", "-alias", "impostor", "-keystore", "impostor.p12", "-file", "impostor.crt");

        X509Certificate ca = ler("ca.crt");
        emitidoPelaAutoridade = new X509Certificate[]{ler("servico.crt"), ca};
        deFora = new X509Certificate[]{ler("impostor.crt")};

        KeyStore confianca = KeyStore.getInstance("PKCS12");
        confianca.load(null, null);
        confianca.setCertificateEntry("ca", ca);
        TrustManagerFactory fabrica = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        fabrica.init(confianca);
        confiaNaAutoridade = (X509TrustManager) fabrica.getTrustManagers()[0];
    }

    /** Um SSLEngine como o do gateway: conectando a um IP, com conferência de nome ligada. */
    private static SSLEngine conectandoA(String ip) throws Exception {
        SSLEngine motor = SSLContext.getDefault().createSSLEngine(ip, 21083);
        motor.setUseClientMode(true);
        SSLParameters parametros = motor.getSSLParameters();
        parametros.setEndpointIdentificationAlgorithm("HTTPS");
        motor.setSSLParameters(parametros);
        return motor;
    }

    @Test
    @DisplayName("certificado emitido pela autoridade é aceito, mesmo com o IP no lugar do nome")
    void aceitaACadeiaCerta() {
        var gerenciador = new ConexaoComOsServicos.CadeiaSemNome(confiaNaAutoridade);
        assertThatCode(() -> gerenciador.checkServerTrusted(emitidoPelaAutoridade, "RSA", conectandoA("172.18.0.12")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("certificado de fora é recusado, ainda que tenha o nome certo — a cadeia continua conferida")
    void recusaQuemNaoFoiEmitidoPelaAutoridade() {
        var gerenciador = new ConexaoComOsServicos.CadeiaSemNome(confiaNaAutoridade);
        assertThatThrownBy(() -> gerenciador.checkServerTrusted(deFora, "RSA", conectandoA("172.18.0.12")))
                .isInstanceOf(CertificateException.class);
        assertThatThrownBy(() -> gerenciador.checkServerTrusted(deFora, "RSA"))
                .isInstanceOf(CertificateException.class);
    }

    @Test
    @DisplayName("as autoridades aceitas são as do gerenciador padrão, nem mais nem menos")
    void mesmasAutoridades() {
        var gerenciador = new ConexaoComOsServicos.CadeiaSemNome(confiaNaAutoridade);
        assertThat(gerenciador.getAcceptedIssuers()).containsExactly(confiaNaAutoridade.getAcceptedIssuers());
    }

    private static X509Certificate ler(String arquivo) throws Exception {
        try (InputStream entrada = new FileInputStream(pasta.resolve(arquivo).toFile())) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(entrada);
        }
    }

    private static void keytool(String... argumentos) throws Exception {
        Path executavel = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool");
        List<String> comando = new ArrayList<>(List.of(executavel.toString(), "-noprompt",
                "-storepass", SENHA, "-keypass", SENHA, "-storetype", "PKCS12"));
        comando.addAll(List.of(argumentos));
        Process processo = new ProcessBuilder(comando).directory(pasta.toFile()).redirectErrorStream(true).start();
        String saida = new String(processo.getInputStream().readAllBytes());
        assertThat(processo.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(processo.exitValue()).as("keytool %s: %s", argumentos[0], saida).isZero();
        assertThat(Files.exists(pasta)).isTrue();
    }
}
