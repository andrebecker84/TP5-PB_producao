package com.andre.infnethub.dto;

import java.time.Instant;

/**
 * O recibo do pedido de aviso.
 *
 * <p>Não diz "avisadas 42 pessoas", porque ninguém foi avisado ainda — e é essa
 * a diferença que a resposta {@code 202} anuncia. Diz o que é verdade no
 * momento em que responde: o pedido foi aceito, está gravado, tem este
 * identificador e sairá a partir deste instante.
 *
 * @param mensagemId o mesmo id que viaja na mensagem e aparece nos cabeçalhos
 *                   do painel do RabbitMQ — é por ele que se acompanha o
 *                   percurso do aviso de ponta a ponta.
 * @param alcance    texto curto para a interface: "toda a turma" ou quantas
 *                   pessoas foram endereçadas.
 * @param entrega    a partir de quando o serviço de notificação pode executar.
 *                   Igual ao momento do pedido quando não há atraso.
 */
public record AvisoResponseDTO(
        String mensagemId,
        String alcance,
        Instant entrega
) {}
