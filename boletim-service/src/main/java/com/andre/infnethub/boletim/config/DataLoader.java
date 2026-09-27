package com.andre.infnethub.boletim.config;

import com.andre.infnethub.boletim.model.*;
import com.andre.infnethub.boletim.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Carga de demonstração — apenas no perfil dev.
 *
 * <p>Grava via JPA, e não por SQL numa migration, pelo mesmo motivo do
 * infnethub-core: o Flyway versiona o <em>schema</em>, e misturar dados de
 * exemplo no mesmo histórico obrigaria a carregá-los também em produção, ou a
 * inventar numeração paralela que quebra a ordem das migrations seguintes.
 *
 * <p>Os dados reproduzem o que as páginas {@code /boletim} e {@code /desempenho}
 * hoje trazem fixos no próprio arquivo. É deliberado: quando o front-end passar
 * a consumir esta API, a tela deve continuar exibindo o mesmo conteúdo — e
 * qualquer diferença que apareça é defeito da integração, não do dado.
 *
 * <p>Ativo nos perfis dev e demo — o segundo é o do Kubernetes do TP5, que roda
 * com a configuração de produção e ainda assim precisa dos dados de exemplo.
 * Ver o DataLoader do infnethub-core.
 */
@Component
@Profile({"dev", "demo"})
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    /** Id do Usuario correspondente no infnethub-core — o aluno do seed de lá. */
    private static final Long ALUNO_DEMO = 1L;

    private final BlocoRepository blocoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final CompetenciaRepository competenciaRepository;
    private final MatriculaRepository matriculaRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final AtividadeAcademicaRepository atividadeRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // A carga roda uma vez. Sem esta guarda, cada reinício duplicaria as
        // matrículas — o volume é nomeado e o banco sobrevive ao contêiner.
        if (blocoRepository.count() > 0) {
            log.info("Carga de demonstração já aplicada — {} blocos no banco.", blocoRepository.count());
            return;
        }

        carregarBloco1();
        carregarBloco2();
        carregarBloco3();
        carregarAtividades();

        log.info("Carga de demonstração aplicada: {} blocos, {} matrículas, {} atividades.",
                blocoRepository.count(), matriculaRepository.count(), atividadeRepository.count());
    }

    private void carregarBloco1() {
        Bloco bloco = novoBloco(1, "Fundamentos do Processamento de Dados");

        cursar(bloco, "Fundamentos do Processamento de Dados", TipoDisciplina.REGULAR, 60, false,
                "25E1", 96, 4, 0, 0,
                List.of("Modelar problemas em estruturas de dados",
                        "Implementar algoritmos de processamento",
                        "Avaliar custo e desempenho de rotinas"),
                conceitos(Conceito.DML, Conceito.DL, Conceito.DML));

        // Isenta de frequência: 62% de presença não a reprova. É o caso que
        // torna a regra visível no boletim de demonstração.
        cursar(bloco, "Planejamento de Curso e Carreira", TipoDisciplina.REGULAR, 30, true,
                "25E1", 62, 2, 0, 0,
                List.of("Traçar um plano de curso coerente com a carreira",
                        "Reconhecer as competências do perfil profissional"),
                conceitos(Conceito.DL, Conceito.D));

        cursar(bloco, "Projeto de Bloco: Processamento de Dados", TipoDisciplina.PROJETO_BLOCO, 60, false,
                "25E1", 92, 5, 0, 0,
                List.of("Integrar as competências do bloco num produto",
                        "Documentar decisões técnicas do projeto",
                        "Apresentar e defender a solução"),
                conceitos(Conceito.DL, Conceito.DL, Conceito.DML));
    }

    private void carregarBloco2() {
        Bloco bloco = novoBloco(2, "Conectividade e Desenvolvimento de Aplicações");

        cursar(bloco, "Conectividade e Desenvolvimento Front-End", TipoDisciplina.REGULAR, 60, false,
                "25E2", 88, 4, 1, 0,
                List.of("Construir interfaces responsivas e acessíveis",
                        "Consumir APIs a partir do cliente",
                        "Gerenciar o estado da aplicação"),
                conceitos(Conceito.DL, Conceito.DL, Conceito.D));

        cursar(bloco, "Desenvolvimento Back-End", TipoDisciplina.REGULAR, 60, false,
                "25E2", 94, 4, 0, 0,
                List.of("Expor serviços REST versionados",
                        "Persistir dados com mapeamento objeto-relacional",
                        "Tratar erros e validações na borda"),
                conceitos(Conceito.DML, Conceito.DML, Conceito.DL));

        cursar(bloco, "Projeto de Bloco: Aplicações Conectadas", TipoDisciplina.PROJETO_BLOCO, 60, false,
                "25E2", 90, 5, 0, 0,
                List.of("Entregar uma aplicação ponta a ponta",
                        "Versionar e publicar o código do projeto",
                        "Justificar a arquitetura adotada"),
                conceitos(Conceito.DML, Conceito.DL, Conceito.DL));
    }

    private void carregarBloco3() {
        Bloco bloco = novoBloco(3, "Ciência da Computação");

        // Bloco em curso: os nulos são competências ainda em avaliação, e são
        // eles que fazem estas disciplinas aparecerem como "Cursando".
        cursar(bloco, "Análise e Segurança de Agentes de IA", TipoDisciplina.REGULAR, 60, false,
                "26E2", 90, 4, 0, 1,
                List.of("Avaliar riscos de agentes autônomos",
                        "Instrumentar e monitorar agentes",
                        "Aplicar guardrails de segurança"),
                conceitos(null, null, null));

        cursar(bloco, "Engenharia Segura de Softwares Escaláveis", TipoDisciplina.REGULAR, 60, false,
                "26E2", 92, 4, 0, 1,
                List.of("Modelar dados com isolamento de domínio",
                        "Integrar JPA com repositórios Spring Data",
                        "Registrar e consultar histórico de dados",
                        "Testar a camada de persistência"),
                conceitos(Conceito.DML, Conceito.DML, Conceito.DL, null));

        cursar(bloco, "Projeto de Bloco: Engenharia de Softwares Escaláveis", TipoDisciplina.PROJETO_BLOCO, 60, false,
                "26E2", 94, 5, 0, 3,
                List.of("Construir a camada de persistência do produto",
                        "Garantir integridade e desempenho no acesso a dados",
                        "Documentar o design da solução"),
                conceitos(Conceito.DML, Conceito.DL, null));
    }

    private void carregarAtividades() {
        atividade(CategoriaAtividade.EXTENSAO, "Portal Comunitário — Projeto Integrador", 120, "25E1", StatusAtividade.CONCLUIDO, null);
        atividade(CategoriaAtividade.EXTENSAO, "Mentoria de Programação em Escola Pública", 80, "25E1", StatusAtividade.CONCLUIDO, null);
        atividade(CategoriaAtividade.EXTENSAO, "Hackathon Social Infnet", 100, "25E2", StatusAtividade.CONCLUIDO, null);
        atividade(CategoriaAtividade.EXTENSAO, "Consultoria de TI para ONGs", 100, "26E2", StatusAtividade.EM_CURSO, null);

        atividade(CategoriaAtividade.ELETIVA, "Computação em Nuvem", 40, "25E1", StatusAtividade.CONCLUIDO, 92);
        atividade(CategoriaAtividade.ELETIVA, "Segurança de Aplicações", 40, "25E2", StatusAtividade.CONCLUIDO, 86);
        atividade(CategoriaAtividade.ELETIVA, "Introdução a Machine Learning", 40, "26E2", StatusAtividade.EM_CURSO, 78);
        atividade(CategoriaAtividade.ELETIVA, "Empreendedorismo em Tecnologia", 40, null, StatusAtividade.NAO_CONCLUIDO, null);

        atividade(CategoriaAtividade.ESTAGIO, "Estágio supervisionado — Desenvolvimento Back-End", 220, "25E2", StatusAtividade.CONCLUIDO, null);
        atividade(CategoriaAtividade.ESTAGIO, "Estágio supervisionado — Plataforma de Dados", 100, "26E2", StatusAtividade.EM_CURSO, null);

        atividade(CategoriaAtividade.COMPLEMENTAR, "Certificação Oracle Java SE", 30, "25E1", StatusAtividade.CONCLUIDO, null);
        atividade(CategoriaAtividade.COMPLEMENTAR, "Semana de Tecnologia Infnet", 16, "25E2", StatusAtividade.CONCLUIDO, null);
        atividade(CategoriaAtividade.COMPLEMENTAR, "Curso de Kubernetes (plataforma externa)", 24, "26E2", StatusAtividade.CONCLUIDO, null);
        atividade(CategoriaAtividade.COMPLEMENTAR, "Monitoria de Estruturas de Dados", 30, "26E2", StatusAtividade.EM_CURSO, null);
    }

    // ── auxiliares ────────────────────────────────────────────────────────

    private Bloco novoBloco(int numero, String titulo) {
        return blocoRepository.save(Bloco.builder().numero(numero).titulo(titulo).build());
    }

    /** Lista de conceitos que aceita nulos — {@code List.of} não aceitaria. */
    private List<Conceito> conceitos(Conceito... valores) {
        List<Conceito> lista = new ArrayList<>();
        for (Conceito valor : valores) {
            lista.add(valor);
        }
        return lista;
    }

    /**
     * Cria a disciplina no catálogo, matricula o aluno e lança os conceitos.
     *
     * <p>Os três passos juntos porque, na carga, eles são um só ato: não há
     * disciplina de demonstração sem alguém cursando.
     */
    private void cursar(Bloco bloco, String nome, TipoDisciplina tipo, int carga, boolean isentaFrequencia,
                        String periodo, int presenca, int tpsTotal, int tpsAtraso, int tpsPendentes,
                        List<String> nomesCompetencias, List<Conceito> conceitos) {

        if (nomesCompetencias.size() != conceitos.size()) {
            throw new IllegalStateException(
                    "Carga inconsistente em '%s': %d competências para %d conceitos."
                            .formatted(nome, nomesCompetencias.size(), conceitos.size()));
        }

        Disciplina disciplina = disciplinaRepository.save(Disciplina.builder()
                .bloco(bloco)
                .nome(nome)
                .tipo(tipo)
                .cargaHoraria(carga)
                .isentaFrequencia(isentaFrequencia)
                .build());

        Matricula matricula = matriculaRepository.save(Matricula.builder()
                .alunoId(ALUNO_DEMO)
                .disciplina(disciplina)
                .periodo(periodo)
                .presencaPercentual(presenca)
                .tpsTotal(tpsTotal)
                .tpsAtraso(tpsAtraso)
                .tpsPendentes(tpsPendentes)
                .build());

        for (int i = 0; i < nomesCompetencias.size(); i++) {
            Competencia competencia = competenciaRepository.save(Competencia.builder()
                    .disciplina(disciplina)
                    .nome(nomesCompetencias.get(i))
                    .ordem(i + 1)
                    .build());

            Avaliacao avaliacao = Avaliacao.builder()
                    .matricula(matricula)
                    .competencia(competencia)
                    .build();
            avaliacao.registrar(conceitos.get(i));
            avaliacaoRepository.save(avaliacao);
        }
    }

    private void atividade(CategoriaAtividade categoria, String nome, int carga,
                           String periodo, StatusAtividade status, Integer presenca) {
        atividadeRepository.save(AtividadeAcademica.builder()
                .alunoId(ALUNO_DEMO)
                .categoria(categoria)
                .nome(nome)
                .cargaHoraria(carga)
                .periodo(periodo)
                .status(status)
                .presencaPercentual(presenca)
                .build());
    }
}
