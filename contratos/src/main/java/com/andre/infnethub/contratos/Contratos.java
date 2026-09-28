package com.andre.infnethub.contratos;

import com.andre.infnethub.contratos.comando.ComandoDeIntegracao;
import com.andre.infnethub.contratos.comando.EnviarAvisoV1;
import com.andre.infnethub.contratos.expurgo.AlunoAnonimizadoV1;
import com.andre.infnethub.contratos.expurgo.AnonimizacaoRecusadaV1;
import com.andre.infnethub.contratos.expurgo.ExpurgoSolicitadoV1;
import com.andre.infnethub.contratos.feed.PostComentadoV1;
import com.andre.infnethub.contratos.feed.PostCurtidoV1;
import com.andre.infnethub.contratos.feed.PostRemovidoV1;
import com.andre.infnethub.contratos.usuario.UsuarioAtualizadoV1;
import com.andre.infnethub.contratos.usuario.UsuarioCadastradoV1;
import com.andre.infnethub.contratos.usuario.UsuarioRemovidoV1;
import com.andre.infnethub.contratos.vaga.VagaPublicadaV1;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * O catálogo dos contratos conhecidos, indexado pelo nome versionado.
 *
 * <p>A caixa de saída guarda cada mensagem como texto, com o nome do tipo ao
 * lado. Para publicá-la, o relay precisa voltar do nome para a classe — e
 * resolver isso por {@code Class.forName} sobre um texto lido do banco seria
 * deixar o conteúdo da tabela decidir que classe carregar. Aqui só o que está
 * listado existe.
 *
 * <p>O catálogo também é o que diz <em>por onde</em> a mensagem sai: um comando
 * vai para a exchange {@code direct}, um evento para a {@code topic}. Essa
 * informação já está no tipo, e por isso não precisa de uma coluna a mais na
 * tabela — uma coluna que, como toda duplicata, poderia discordar do tipo.
 */
public final class Contratos {

    private static final Map<String, Class<? extends MensagemDeIntegracao>> POR_TIPO = Stream.of(
                    UsuarioCadastradoV1.class,
                    UsuarioAtualizadoV1.class,
                    UsuarioRemovidoV1.class,
                    PostCurtidoV1.class,
                    PostComentadoV1.class,
                    PostRemovidoV1.class,
                    VagaPublicadaV1.class,
                    ExpurgoSolicitadoV1.class,
                    AlunoAnonimizadoV1.class,
                    AnonimizacaoRecusadaV1.class,
                    EnviarAvisoV1.class)
            .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, Function.identity()));

    private Contratos() {
    }

    public static Optional<Class<? extends MensagemDeIntegracao>> classeDe(String tipo) {
        return Optional.ofNullable(POR_TIPO.get(tipo));
    }

    /** Todos os contratos conhecidos — usado pelo teste que os verifica. */
    public static Collection<Class<? extends MensagemDeIntegracao>> todos() {
        return POR_TIPO.values();
    }

    /**
     * A exchange por onde a mensagem sai, deduzida do que ela é.
     *
     * <p>Fato vai para a {@code topic}, onde cada interessado se liga ao que
     * quer ouvir. Pedido vai para a {@code direct}, onde a chave é o endereço
     * de um executor só.
     */
    public static String exchangeDe(MensagemDeIntegracao mensagem) {
        return mensagem instanceof ComandoDeIntegracao ? Canais.EXCHANGE_COMANDOS : Canais.EXCHANGE_EVENTOS;
    }
}
