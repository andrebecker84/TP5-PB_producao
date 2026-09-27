package com.andre.infnethub.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * O pedido de aviso, como a secretaria o escreve.
 *
 * <p>Repare no que <strong>não</strong> está aqui: quem está pedindo. Esse
 * dado vem do token, e não do corpo. Foi exatamente a lição do TP3, onde a
 * identidade chegava num cabeçalho que qualquer um podia escrever — um campo
 * {@code solicitadoPorId} neste record seria a mesma falha com outra roupa.
 *
 * @param destinatarios a quem avisar. Omitido ou vazio significa
 *                      <em>todos os alunos ativos</em>.
 * @param atrasoSegundos quanto esperar antes de entregar. Zero ou omitido
 *                      entrega assim que o relay publicar. O teto de 30 dias
 *                      não é capricho: o prazo vira o tempo de validade da
 *                      mensagem no broker, e uma mensagem parada numa fila é
 *                      espaço em disco ocupado o tempo todo.
 */
public record AvisoRequestDTO(

        List<Long> destinatarios,

        @NotBlank(message = "Texto do aviso é obrigatório")
        @Size(max = 500, message = "O aviso deve caber em 500 caracteres")
        String texto,

        @Size(max = 200)
        String link,

        @Min(value = 0, message = "O atraso não pode ser negativo")
        @Max(value = 2_592_000, message = "O atraso máximo é de 30 dias")
        Long atrasoSegundos

) {
    /** Zero e ausência significam a mesma coisa: entregar agora. */
    public long atrasoEmSegundos() {
        return atrasoSegundos == null ? 0L : atrasoSegundos;
    }
}
