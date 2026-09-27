package com.andre.infnethub.boletim.aluno;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A cópia local do que o boletim precisa saber sobre um aluno.
 *
 * <p>Não é dono de nada: quem decide o nome, a turma e o papel é o core. Esta
 * linha só muda quando chega um evento dizendo que lá mudou — nenhum endpoint
 * deste serviço a altera. Uma réplica editável por dois lados divergiria, e não
 * haveria como saber qual dos dois está certo.
 */
@Entity
@Table(name = "aluno_replica")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AlunoReplica {

    /** O id do Usuario no core. Atribuído lá, nunca gerado aqui. */
    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 100)
    private String escola;

    @Column(name = "ultimo_bloco", length = 50)
    private String ultimoBloco;

    @Column(length = 20)
    private String classe;

    @Column(nullable = false, length = 20)
    private String papel;

    @Column(name = "papel_descricao", length = 50)
    private String papelDescricao;

    @Column(name = "versao_origem", nullable = false)
    private long versaoOrigem;

    @Column(name = "sincronizado_em", nullable = false)
    private LocalDateTime sincronizadoEm;

    /** Este estado é mais novo que o outro, segundo a versão do core? */
    boolean maisNovoQue(AlunoReplica outro) {
        return this.versaoOrigem > outro.versaoOrigem;
    }
}
