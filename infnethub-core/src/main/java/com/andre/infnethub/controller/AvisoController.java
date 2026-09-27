package com.andre.infnethub.controller;

import com.andre.infnethub.dto.AvisoRequestDTO;
import com.andre.infnethub.dto.AvisoResponseDTO;
import com.andre.infnethub.seguranca.PapeisDoKeycloak;
import com.andre.infnethub.service.AvisoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O canal da secretaria e dos professores para falar com a turma.
 *
 * <h2>202, e não 201</h2>
 * <p>{@code 201 Created} diria que existe agora um recurso novo em algum lugar,
 * com endereço próprio — e não existe: as notificações ainda não foram
 * escritas, e serão escritas por outro serviço, no banco dele.
 * {@code 202 Accepted} diz exatamente o que aconteceu: o pedido foi aceito e
 * será processado. É o código feito para trabalho assíncrono, e usá-lo aqui é
 * o mesmo cuidado que se tem ao nomear uma variável — a resposta não deve
 * prometer mais do que o sistema cumpriu.
 *
 * <p>Quem chama não fica sem informação: o recibo traz o identificador da
 * mensagem, o mesmo que aparece no painel do RabbitMQ e nos registros do
 * serviço de notificação.
 */
@RestController
@RequestMapping("/api/v1/avisos")
@RequiredArgsConstructor
public class AvisoController {

    private final AvisoService avisoService;

    /**
     * Quem pede é lido do token, nunca do corpo. O Spring injeta aqui o mesmo
     * {@code Jwt} que a cadeia de segurança já validou — não há segunda
     * verificação nem lugar para o cliente opinar sobre quem é.
     */
    @PostMapping
    public ResponseEntity<AvisoResponseDTO> enviar(@Valid @RequestBody AvisoRequestDTO dto,
                                                   @AuthenticationPrincipal Jwt token) {
        Long solicitante = PapeisDoKeycloak.usuarioIdDe(token);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(avisoService.enviar(dto, solicitante));
    }
}
