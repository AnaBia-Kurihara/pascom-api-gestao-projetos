package br.org.pascom.dto;

import java.util.List;

/** acoes: log em texto simples de cada ação de verdade que o agente executou nessa mensagem. */
public record AgenteRespostaDTO(
        String resposta,
        List<String> acoes
) {}
