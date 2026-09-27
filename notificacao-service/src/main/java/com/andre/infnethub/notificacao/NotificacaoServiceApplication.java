package com.andre.infnethub.notificacao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Microsserviço de Notificações.
 *
 * <p>Nasce no TP4, e nasce inteiramente orientado a eventos. Não há endpoint que
 * outro serviço chame para criar uma notificação: o core publica que alguém
 * curtiu, comentou ou abriu uma vaga, e este serviço decide, sozinho, quem deve
 * ser avisado. O core não sabe que ele existe.
 *
 * <p>No TP3 as notificações do front-end eram uma lista fixa no próprio código
 * da interface. Não havia onde criá-las, porque nenhum serviço sabia que uma
 * curtida deveria virar aviso para o autor do post.
 */
@SpringBootApplication
@EnableScheduling
public class NotificacaoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificacaoServiceApplication.class, args);
    }
}
