package br.org.pascom.dto;

import jakarta.validation.constraints.NotBlank;

/** papel: "usuario" ou "agente" — um item do histórico da conversa. */
public record AgenteTurnoDTO(
        @NotBlank String papel,
        @NotBlank String texto
) {}
