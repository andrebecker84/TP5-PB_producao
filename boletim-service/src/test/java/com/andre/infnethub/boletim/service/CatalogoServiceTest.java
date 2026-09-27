package com.andre.infnethub.boletim.service;

import com.andre.infnethub.boletim.CenarioAcademico;
import com.andre.infnethub.boletim.exception.ResourceNotFoundException;
import com.andre.infnethub.boletim.model.Bloco;
import com.andre.infnethub.boletim.model.Conceito;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O catálogo do curso.
 *
 * <p>Existe por causa de um defeito real: a primeira versão buscava blocos,
 * disciplinas e competências num único {@code JOIN FETCH} e devolvia 500 com
 * {@code MultipleBagFetchException} — o Hibernate recusa buscar duas coleções
 * {@code List} de uma vez. Passou no build e só apareceu ao chamar o endpoint.
 *
 * <p>O teste abaixo é a rede que impede a volta: qualquer tentativa de fundir as
 * duas consultas em uma quebra aqui, e não em produção.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("CatalogoService — estrutura do curso")
class CatalogoServiceTest {

    private static final AtomicInteger PROXIMO_BLOCO = new AtomicInteger(400);

    @Autowired private CatalogoService catalogoService;
    @Autowired private CenarioAcademico cenario;

    @Test
    @DisplayName("carrega blocos, disciplinas E competências sem MultipleBagFetchException")
    void catalogoCompletoSemDuploFetch() {
        Bloco bloco = cenario.bloco(PROXIMO_BLOCO.incrementAndGet(), "Bloco do catálogo");
        var matricula = cenario.matricula(5001L,
                cenario.disciplina(bloco, "Disciplina catalogada", 60, false), "26E2", 90, 4, 0, 0);
        cenario.avaliar(matricula, Conceito.DML, Conceito.DL, Conceito.D);

        assertThatCode(() -> catalogoService.listarBlocos()).doesNotThrowAnyException();

        var encontrado = catalogoService.listarBlocos().stream()
                .filter(b -> b.numero().equals(bloco.getNumero())).findFirst().orElseThrow();

        // As competências precisam estar inicializadas: é a segunda consulta,
        // na mesma transação, que as traz. Sem ela a sessão fecharia antes.
        assertThat(encontrado.disciplinas()).hasSize(1);
        assertThat(encontrado.disciplinas().get(0).competencias()).hasSize(3);
        assertThat(encontrado.disciplinas().get(0).competencias())
                .extracting("ordem").containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("buscar um bloco pelo número também traz as competências")
    void blocoIndividual() {
        Bloco bloco = cenario.bloco(PROXIMO_BLOCO.incrementAndGet(), "Bloco individual");
        var matricula = cenario.matricula(5002L,
                cenario.disciplina(bloco, "Única", 60, false), "26E2", 90, 4, 0, 0);
        cenario.avaliar(matricula, Conceito.DL);

        var encontrado = catalogoService.buscarBloco(bloco.getNumero());

        assertThat(encontrado.titulo()).isEqualTo("Bloco individual");
        assertThat(encontrado.disciplinas().get(0).competencias()).hasSize(1);
    }

    @Test
    @DisplayName("bloco inexistente é 404")
    void blocoInexistente() {
        assertThatThrownBy(() -> catalogoService.buscarBloco(9999))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("9999");
    }

    @Test
    @DisplayName("a escala de conceitos sai do pior para o melhor, com nome, regra e peso")
    void escalaDeConceitos() {
        // É por este endpoint que a legenda do boletim deixou de manter a sua
        // própria cópia dos textos.
        assertThat(catalogoService.listarConceitos())
                .extracting("codigo").containsExactly("ND", "D", "DL", "DML");
        assertThat(catalogoService.listarConceitos())
                .extracting("peso").containsExactly(0, 60, 80, 100);
        assertThat(catalogoService.listarConceitos().get(3).nome())
                .isEqualTo("Demonstrou com Máximo Louvor");
        assertThat(catalogoService.listarConceitos().get(0).regra()).isNotBlank();
    }
}
