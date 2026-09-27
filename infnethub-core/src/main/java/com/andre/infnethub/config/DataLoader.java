package com.andre.infnethub.config;

import com.andre.infnethub.mensageria.EventosDeUsuario;
import com.andre.infnethub.model.*;
import com.andre.infnethub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Carga de demonstração.
 *
 * <p>Grava pelos repositórios, e não por SQL de migration, para que os dados
 * iniciais nasçam com histórico: cada registro criado aqui gera sua revisão de
 * criação no Envers, atribuída ao autor {@code sistema} — não há requisição HTTP
 * no start da aplicação. Sem isso, o histórico dos registros semeados começaria
 * vazio e a auditoria teria um ponto cego bem no dado mais visível da aplicação.
 *
 * <p>Restrito aos perfis dev e demo: em produção a base não é semeada, e nos
 * testes cada classe monta os próprios dados. O perfil demo existe desde o TP5:
 * o Kubernetes roda com a configuração de produção ({@code prod,k8s}), e a
 * demonstração precisa das contas que o realm do Keycloak espera — basta tirar
 * {@code demo} da lista de perfis para ter a base vazia de uma implantação
 * real. Reexecutar é seguro, pois a carga só ocorre com a base vazia.
 */
@Component
@Profile({"dev", "demo"})
@RequiredArgsConstructor
public class DataLoader implements ApplicationRunner {

    private final UsuarioRepository    usuarioRepository;
    private final PostRepository       postRepository;
    private final VagaRepository       vagaRepository;
    private final CurtidaRepository    curtidaRepository;
    private final ComentarioRepository comentarioRepository;
    private final JdbcTemplate         jdbc;
    private final EventosDeUsuario     eventos;
    private final TransactionTemplate  transacao;

    /**
     * Id fixo da conta de demonstração do suporte de TI. O Keycloak liga a conta
     * ao cadastro por este número (atributo usuario_id em
     * infra/keycloak/realm-infnethub.json), e ele precisa ser o mesmo num banco
     * recém-criado e num que já tem dados.
     */
    static final long ID_DO_SUPORTE_DE_TI = 1000L;

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.count() == 0) {
            semear();
        }
        // Depois da carga, e não antes: num banco novo, a sequência levada ao
        // id fixo faria os usuários de demonstração perderem os ids 1 a 5, que
        // o realm do Keycloak espera.
        garantirContaDoSuporteDeTi();
    }

    private void semear() {

        // ── Usuários ───────────────────────────────────────────────
        Usuario lucas = cadastrar(Usuario.builder()
                .nome("Lucas Mendonça").email("lucas.mendonca@hub.infnet.local")
                .escola("Faculdade Infnet").ultimoBloco("Bloco 5").classe("26E2")
                .papel(Papel.ALUNO).build());

        Usuario carlos = cadastrar(Usuario.builder()
                .nome("Prof. Carlos Oliveira").email("carlos.oliveira@hub.infnet.local")
                .escola("Faculdade Infnet").ultimoBloco("Professor")
                .papel(Papel.PROFESSOR).build());

        Usuario secretaria = cadastrar(Usuario.builder()
                .nome("Atendimento Infnet Hub").email("atendimento@hub.infnet.local")
                .escola("Faculdade Infnet")
                .papel(Papel.SECRETARIA).build());

        Usuario mariana = cadastrar(Usuario.builder()
                .nome("Mariana Ferreira").email("mariana.ferreira@hub.infnet.local")
                .escola("Faculdade Infnet").ultimoBloco("Bloco 5").classe("26E2")
                .papel(Papel.ALUNO).build());

        Usuario rafael = cadastrar(Usuario.builder()
                .nome("Rafael Azevedo").email("rafael.azevedo@hub.infnet.local")
                .escola("Faculdade Infnet").ultimoBloco("Bloco 5").classe("26E1")
                .papel(Papel.ALUNO).build());

        // ── Posts ──────────────────────────────────────────────────
        Post p1 = postRepository.save(Post.builder()
                .titulo("Bem-vindos ao Bloco 5!")
                .conteudo("Olá turma! Começamos mais um bloco com muitos desafios pela frente. Este semestre vamos explorar microsserviços, eventos assíncronos e muito mais. Preparem-se!")
                .autor(carlos).curtidas(0).build());

        Post p2 = postRepository.save(Post.builder()
                .conteudo("Alguém já começou o TP2? Subi o PostgreSQL pelo Docker Compose, mas a aplicação não sobe: o Hibernate reclama que a tabela `comentarios` não existe. As migrations do Flyway não parecem estar rodando.")
                .imagemUrl("/images/posts/persistencia.svg")
                .autor(lucas).curtidas(0).build());

        Post p3 = postRepository.save(Post.builder()
                .titulo("Participe do Colegiado e faça a diferença!")
                .conteudo("Essa é a sua chance de contribuir diretamente para melhorias no curso e representar seus colegas. A participação na reunião do Colegiado garante 1 hora de Atividades Complementares. Serão selecionados até dois alunos por curso.")
                .autor(secretaria).curtidas(0).build());

        Post p4 = postRepository.save(Post.builder()
                .conteudo("Dica para quem travou no TP2: no Spring Boot 4 o `flyway-core` sozinho não roda as migrations — ele entra no classpath, mas nada o liga ao ciclo de vida da aplicação. Falta a dependência `org.springframework.boot:spring-boot-flyway`. As autoconfigurações foram divididas em módulos e a maioria dos tutoriais ainda não cobre isso.")
                .autor(carlos).curtidas(0).build());

        Post p5 = postRepository.save(Post.builder()
                .titulo("Aula ao vivo — Arquitetura de Microsserviços")
                .conteudo("Na aula de hoje vamos decompor o monolito em microsserviços independentes usando Spring Cloud Gateway e Eureka para service discovery. Confirme presença no link da aula!")
                .imagemUrl("/images/posts/agentes-ia.svg")
                .autor(carlos).curtidas(0).build());

        // ── Posts ilustrados ───────────────────────────────────────
        Post p6 = postRepository.save(Post.builder()
                .titulo("Hackathon Social Infnet — inscrições abertas")
                .conteudo("Três dias para resolver um problema real de uma ONG parceira, em equipes de até cinco alunos. Vale 100h de Projeto Supervisionado de Extensão e as três primeiras colocações apresentam na Semana de Tecnologia. Inscrições até sexta.")
                .imagemUrl("/images/posts/hackathon.svg")
                .autor(secretaria).curtidas(0).build());

        Post p7 = postRepository.save(Post.builder()
                .titulo("Estágio em back-end Java — empresa parceira")
                .conteudo("Vaga para estágio de 30h semanais em Java com Spring Boot e PostgreSQL, presencial no Rio. Conta como Estágio Obrigatório e a empresa aceita alunos a partir do segundo bloco. Currículos pela aba Vagas.")
                .imagemUrl("/images/posts/vaga-estagio.svg")
                .autor(secretaria).curtidas(0).build());

        Post p8 = postRepository.save(Post.builder()
                .titulo("Prazo limite dos TPs desta semana")
                .conteudo("Lembrete: o prazo normal de entrega encerra na segunda-feira. Um TP entregue depois disso limita os conceitos do AT a DL; dois ou mais limitam a D. Confira no Moodle se o arquivo subiu de verdade — a responsabilidade pelo envio é de vocês.")
                .imagemUrl("/images/posts/calendario-tp.svg")
                .autor(carlos).curtidas(0).build());

        // ── Curtidas ───────────────────────────────────────────────
        curtir(p1, List.of(lucas, mariana, rafael));
        curtir(p2, List.of(carlos, mariana, rafael));
        curtir(p3, List.of(lucas, carlos, mariana, rafael));
        curtir(p6, List.of(lucas, mariana, rafael, carlos));
        curtir(p7, List.of(lucas, rafael));
        curtir(p8, List.of(mariana, rafael));
        curtir(p4, List.of(lucas, mariana, rafael));
        curtir(p5, List.of(lucas, mariana, rafael));

        // ── Comentários ────────────────────────────────────────────
        comentarioRepository.save(Comentario.builder()
                .conteudo("Animado para esse bloco! Vamos nessa!")
                .post(p1).autor(lucas).build());
        comentarioRepository.save(Comentario.builder()
                .conteudo("Que início empolgante! Mal posso esperar pelas aulas práticas de microsserviços.")
                .post(p1).autor(mariana).build());

        comentarioRepository.save(Comentario.builder()
                .conteudo("Confira o log do start: se não aparecer nenhuma linha do Flyway, ele nem chegou a rodar. Com `ddl-auto=validate` o Hibernate só confere o schema — ele não cria tabela nenhuma, e é por isso que a falha aparece como tabela inexistente.")
                .post(p2).autor(carlos).build());
        comentarioRepository.save(Comentario.builder()
                .conteudo("Tive o mesmo problema! No meu caso o banco também estava na porta errada: a 5432 já estava ocupada por outro projeto e eu não tinha percebido. A variável DB_PORT resolve.")
                .post(p2).autor(mariana).build());
        comentarioRepository.save(Comentario.builder()
                .conteudo("Valeu pessoal! Consegui resolver seguindo essas dicas.")
                .post(p2).autor(lucas).build());

        comentarioRepository.save(Comentario.builder()
                .conteudo("Funcionou aqui! Muito obrigado, prof. Era exatamente isso.")
                .post(p4).autor(lucas).build());
        comentarioRepository.save(Comentario.builder()
                .conteudo("Salvou demais! Estava quebrando a cabeça com esse erro há horas.")
                .post(p4).autor(rafael).build());
        comentarioRepository.save(Comentario.builder()
                .conteudo("Dica de ouro! Vou adicionar isso no meu setup padrão de todo projeto Spring Boot.")
                .post(p4).autor(mariana).build());

        comentarioRepository.save(Comentario.builder()
                .conteudo("Já confirmei presença! Muito animado com esse tema — era exatamente o que precisava para o TP3.")
                .post(p5).autor(rafael).build());
        comentarioRepository.save(Comentario.builder()
                .conteudo("Microsserviços com Spring Cloud Gateway é exatamente o que estava esperando. Vejo vocês na aula!")
                .post(p5).autor(mariana).build());

        // ── Vagas ──────────────────────────────────────────────────
        vagaRepository.save(Vaga.builder()
                .titulo("Desenvolvedor Java Backend").empresa("TechSolutions Brasil")
                .descricao("Desenvolvimento de APIs REST com Spring Boot, JPA e PostgreSQL. Experiência com Docker e CI/CD é diferencial.")
                .localizacao("São Paulo, SP").tipo(TipoVaga.CLT).categoria("Backend")
                .criador(secretaria).build());

        vagaRepository.save(Vaga.builder()
                .titulo("Estágio em Desenvolvimento React").empresa("Startup Digital Rio")
                .descricao("Desenvolvimento de interfaces web com React e TypeScript. Ideal para estudantes de Engenharia de Software.")
                .localizacao("Rio de Janeiro, RJ (Híbrido)").tipo(TipoVaga.ESTAGIO).categoria("Frontend")
                .criador(secretaria).build());

        vagaRepository.save(Vaga.builder()
                .titulo("Desenvolvedor Full Stack").empresa("Fintech Carioca")
                .descricao("Stack: Node.js + React + PostgreSQL. Atuação em produto financeiro de alto crescimento. 100% remoto.")
                .localizacao("Remoto").tipo(TipoVaga.PJ).categoria("Full Stack")
                .criador(secretaria).build());

        vagaRepository.save(Vaga.builder()
                .titulo("Mobile Developer React Native").empresa("AppWorks Solutions")
                .descricao("Desenvolvimento de aplicativos iOS e Android com React Native. Publicação nas lojas e integração com APIs REST.")
                .localizacao("São Paulo, SP").tipo(TipoVaga.CLT).categoria("Mobile")
                .criador(secretaria).build());

        vagaRepository.save(Vaga.builder()
                .titulo("Trainee Engenharia de Software").empresa("Banco Digital BR")
                .descricao("Programa trainee de 18 meses com rotação em times de backend, infraestrutura e dados. Formação técnica intensiva.")
                .localizacao("São Paulo, SP").tipo(TipoVaga.TRAINEE).categoria("Trainee")
                .criador(secretaria).build());

        // ── Datas escalonadas ──────────────────────────────────────
        // O @CreatedDate grava tudo em now(): sem isto, o feed e a linha do
        // tempo ficariam no mesmo instante. Ajuste por SQL nativo (não passa
        // pelo listener de auditoria), espalhando os posts ao longo de semanas.
        escalonarPost(p1, 24, 9);
        escalonarPost(p2, 16, 20);
        escalonarPost(p3, 9, 5);
        escalonarPost(p4, 4, 12);
        escalonarPost(p5, 1, 3);
        // comentários poucas horas após o respectivo post (nunca no futuro)
        jdbc.update("UPDATE comentarios c SET criado_em = p.criado_em + ((c.id % 5 + 1) * INTERVAL '2 hours'), "
                  + "atualizado_em = p.criado_em + ((c.id % 5 + 1) * INTERVAL '2 hours') "
                  + "FROM posts p WHERE c.post_id = p.id");
        // vagas espalhadas nos últimos dias
        jdbc.update("UPDATE vagas SET criado_em = NOW() - (id * INTERVAL '2 days'), "
                  + "atualizado_em = NOW() - (id * INTERVAL '2 days')");
    }

    /**
     * Grava o usuário e anuncia o cadastro, numa transação só.
     *
     * <p>Os usuários semeados precisam chegar ao boletim como qualquer outro:
     * gravados direto pelo repositório, não gerariam evento, e a réplica do
     * boletim nasceria sem os alunos da demonstração. Uma transação por
     * usuário, e não uma para a carga toda, mantém cada cadastro na sua
     * própria revisão do Envers, como já era.
     */
    private Usuario cadastrar(Usuario usuario) {
        return transacao.execute(status -> {
            Usuario salvo = usuarioRepository.save(usuario);
            eventos.cadastrado(salvo);
            return salvo;
        });
    }

    /** Reescreve criado_em/atualizado_em de um post para N dias e H horas atrás. */
    private void escalonarPost(Post p, long dias, long horas) {
        LocalDateTime d = LocalDateTime.now().minusDays(dias).minusHours(horas);
        jdbc.update("UPDATE posts SET criado_em = ?, atualizado_em = ? WHERE id = ?", d, d, p.getId());
    }

    private void curtir(Post post, List<Usuario> usuarios) {
        usuarios.forEach(u ->
            curtidaRepository.save(Curtida.builder().post(post).usuario(u).build())
        );
        post.setCurtidas(usuarios.size());
        postRepository.save(post);
    }

    /**
     * A conta de demonstração do suporte de TI — criada também num banco que
     * já tem dados, porque o papel chegou depois da carga inicial.
     *
     * <p>Por SQL, e não pelo repositório, porque o id é fixo: a coluna aceita
     * valor explícito ({@code GENERATED BY DEFAULT}), mas o JPA sempre pediria
     * um novo. Em seguida a sequência é levada adiante, para o próximo
     * cadastro não tentar o mesmo número. Não passa pela caixa de saída: a
     * conta não é de aluno, e nenhum outro serviço precisa saber dela.
     */
    private void garantirContaDoSuporteDeTi() {
        jdbc.update("""
                INSERT INTO usuarios (id, nome, email, papel, criado_em, atualizado_em,
                                      criado_por, atualizado_por, versao, removido)
                VALUES (?, 'Suporte de TI', 'suporte.ti@hub.infnet.local', 'SUPORTE_TI',
                        now(), now(), 'sistema', 'sistema', 0, false)
                ON CONFLICT (id) DO NOTHING
                """, ID_DO_SUPORTE_DE_TI);
        jdbc.execute("""
                SELECT setval(pg_get_serial_sequence('usuarios', 'id'),
                              GREATEST((SELECT MAX(id) FROM usuarios),
                                       (SELECT last_value FROM usuarios_id_seq)))
                """);
    }
}
