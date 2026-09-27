package com.andre.infnethub.service.impl;

import com.andre.infnethub.contratos.Canais;
import com.andre.infnethub.contratos.comando.EnviarAvisoV1;
import com.andre.infnethub.dto.AvisoRequestDTO;
import com.andre.infnethub.dto.AvisoResponseDTO;
import com.andre.infnethub.mensageria.CaixaDeSaida;
import com.andre.infnethub.service.AvisoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * O lado de quem pede: monta o comando e o entrega à caixa de saída.
 *
 * <h2>Por que isto é tão curto</h2>
 * <p>Porque o trabalho de verdade — descobrir quem são os alunos ativos,
 * escrever uma linha para cada um, avisar o sino de quem está com a tela
 * aberta — não acontece aqui, e é esse o ponto do padrão. O core não sabe
 * quantas pessoas serão avisadas, e não espera para descobrir: grava o pedido e
 * responde. Se o serviço de notificação estiver fora do ar, a secretaria nem
 * fica sabendo, porque não faz diferença para ela.
 *
 * <p>Comparado com o que o TP3 faria — uma chamada HTTP ao serviço de
 * notificação, síncrona, que falharia junto com ele —, a diferença é a única
 * que importa: aqui o pedido sobrevive ao destinatário estar fora.
 */
@Service
@RequiredArgsConstructor
public class AvisoServiceImpl implements AvisoService {

    private static final Logger log = LoggerFactory.getLogger(AvisoServiceImpl.class);

    private final CaixaDeSaida caixa;

    @Override
    @Transactional
    public AvisoResponseDTO enviar(AvisoRequestDTO dto, Long solicitadoPorId) {
        List<Long> destinatarios = dto.destinatarios() == null ? List.of() : dto.destinatarios();
        Duration atraso = Duration.ofSeconds(dto.atrasoEmSegundos());
        Instant agora = Instant.now();

        EnviarAvisoV1 comando = new EnviarAvisoV1(
                UUID.randomUUID(), agora, destinatarios, dto.texto(), dto.link(), solicitadoPorId);

        // O endereço é um só — o executor de avisos. O que separa "faça agora"
        // de "faça depois" é o atraso gravado junto: com ele, o relay publica na
        // sala de espera, e é o broker que conta o tempo.
        if (atraso.isZero()) {
            caixa.depositar(comando, Canais.ROTA_ENVIAR_AVISO);
        } else {
            caixa.agendar(comando, Canais.ROTA_ENVIAR_AVISO, atraso);
        }

        log.info("aviso {} aceito de {} para {}{}", comando.mensagemId(), solicitadoPorId,
                comando.paraTodosOsAlunos() ? "toda a turma" : destinatarios.size() + " pessoa(s)",
                atraso.isZero() ? "" : ", com atraso de " + atraso.toSeconds() + "s");

        return new AvisoResponseDTO(
                comando.mensagemId().toString(),
                comando.paraTodosOsAlunos() ? "toda a turma" : destinatarios.size() + " pessoa(s)",
                agora.plus(atraso));
    }
}
