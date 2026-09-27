package com.andre.infnethub.notificacao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Alguém que pode receber aviso de vaga nova.
 *
 * <p>É o mínimo de estado que este serviço precisa guardar sobre usuários: quem
 * existe e qual papel tem. Nome e e-mail não entram — o texto de cada
 * notificação chega pronto no evento que a origina.
 */
@Entity
@Table(name = "destinatario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Destinatario {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 20)
    private String papel;

    @Column(name = "versao_origem", nullable = false)
    private long versaoOrigem;

    @Column(nullable = false)
    private boolean removido;

    public Destinatario(Long usuarioId, String papel, long versaoOrigem) {
        this.usuarioId = usuarioId;
        this.papel = papel;
        this.versaoOrigem = versaoOrigem;
    }

    /**
     * A marca de quem foi removido antes de o cadastro chegar aqui.
     *
     * <p>Com consumidores em paralelo, a remoção pode ser processada primeiro.
     * Sem a lápide, o cadastro atrasado criaria um destinatário para alguém que
     * já não existe.
     */
    public static Destinatario lapide(Long usuarioId) {
        Destinatario d = new Destinatario(usuarioId, "REMOVIDO", Long.MAX_VALUE);
        d.removido = true;
        return d;
    }

    /**
     * Aplica um estado mais novo. Devolve {@code false} se o recebido for mais
     * velho que o guardado, ou se o destinatário já foi removido — um removido
     * não volta por causa de um evento atrasado.
     */
    public boolean atualizar(String novoPapel, long novaVersao) {
        if (removido || novaVersao <= versaoOrigem) {
            return false;
        }
        this.papel = novoPapel;
        this.versaoOrigem = novaVersao;
        return true;
    }

    public void remover() {
        this.removido = true;
    }
}
