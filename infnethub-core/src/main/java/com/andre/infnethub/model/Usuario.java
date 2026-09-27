package com.andre.infnethub.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

/** Aggregate Root — Bounded Context: Identidade */
@Entity
@Table(
        name = "usuarios",
        indexes = {
                @Index(name = "idx_usuarios_papel", columnList = "papel")
        }
)
@Audited
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario extends EntidadeAuditavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    /** Identidade institucional — a unicidade é garantida no banco. */
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 100)
    private String escola;

    @Column(name = "ultimo_bloco", length = 50)
    private String ultimoBloco;

    @Column(length = 20)
    private String classe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Papel papel = Papel.ALUNO;

    /**
     * Fora do sistema — bloqueado durante a saga de expurgo, ou já anonimizado
     * por ela.
     *
     * <p>Um único sinalizador para os dois estados, porque para quem consulta a
     * API eles são o mesmo: a pessoa não existe mais. A diferença entre
     * "bloqueado, e pode voltar" e "anonimizado, e não volta" interessa à saga,
     * e está na tabela {@code expurgo_lgpd}.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean removido = false;

    /**
     * Substitui os dados pessoais por marcas sem conteúdo, mantendo o registro.
     *
     * <p>É o passo final da saga, e é a resposta correta sob a LGPD para quem
     * publicou conteúdo: apagar a linha exigiria apagar também os posts e
     * comentários que dependem dela, destruindo o que outras pessoas
     * escreveram. O dado anonimizado deixa de ser dado pessoal (art. 12), e o
     * conteúdo continua de pé, agora sem autor identificado.
     *
     * <p>O e-mail vira um endereço único e impossível de entregar: único porque
     * a coluna exige, e no domínio reservado {@code .invalid} (RFC 2606) porque
     * ninguém deve conseguir escrever para ele por engano.
     */
    public void anonimizar() {
        this.nome = "Usuário removido";
        this.email = "removido+" + id + "@infnethub.invalid";
        this.escola = null;
        this.ultimoBloco = null;
        this.classe = null;
        this.removido = true;
    }
}
