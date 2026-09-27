package com.andre.infnethub.contratos.vaga;

import com.andre.infnethub.contratos.EventoDeIntegracao;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma vaga nova foi aberta no mural de oportunidades.
 *
 * <p>Diferente dos eventos de usuário, este não transfere o estado completo da
 * vaga — descrição, localização e categoria ficam no core. É um evento de
 * <em>notificação</em>: diz o bastante para alguém decidir se quer saber mais,
 * e quem quiser abre a vaga pela API. A escolha entre um estilo e outro é por
 * consumidor: o boletim precisa do nome do aluno para funcionar sozinho, e por
 * isso o recebe; um aviso de vaga só precisa ser lido.
 */
public record VagaPublicadaV1(
        UUID mensagemId,
        Instant ocorridoEm,
        Long vagaId,
        String titulo,
        String empresa,
        String tipo,
        Long publicadaPorId
) implements EventoDeIntegracao {

    @Override
    public String chave() {
        return String.valueOf(vagaId);
    }
}
