package com.andre.infnethub.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

/** Aggregate Root — Bounded Context: Feed */
@Entity
@Table(
        name = "posts",
        indexes = {
                // Sustenta a consulta principal do feed, ordenada por data decrescente.
                @Index(name = "idx_posts_criado_em", columnList = "criado_em"),
                @Index(name = "idx_posts_autor", columnList = "autor_id")
        }
)
@Audited
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String conteudo;

    /**
     * Capa opcional do post. Guarda apenas a REFERÊNCIA (caminho ou URL) — o
     * binário nunca entra na linha: manter blobs na tabela do feed inflaria
     * cada leitura da consulta principal, que é ordenada e paginada.
     * É auditada: trocar a imagem de um post é uma edição editorial e precisa
     * aparecer no histórico junto do texto.
     */
    @Column(name = "imagem_url", length = 500)
    private String imagemUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_posts_autor"))
    private Usuario autor;

    /**
     * Contador desnormalizado: poupa um {@code COUNT} sobre {@code curtidas} a
     * cada item do feed. Fica fora da auditoria por ser valor derivado — a
     * verdade está nas linhas de {@code curtidas}, e auditá-lo criaria uma
     * revisão do post a cada clique em curtir, poluindo o histórico editorial.
     */
    @Builder.Default
    @NotAudited
    @Column(nullable = false)
    private Integer curtidas = 0;

    @PrePersist
    protected void aoCriar() {
        if (curtidas == null) curtidas = 0;
    }
}
