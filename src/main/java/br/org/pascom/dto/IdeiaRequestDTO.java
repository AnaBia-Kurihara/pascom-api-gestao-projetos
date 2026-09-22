package br.org.pascom.dto;

import jakarta.validation.constraints.NotBlank;

public record IdeiaRequestDTO(
        @NotBlank(message = "O título é obrigatório") String titulo,
        String descricao,
        @NotBlank(message = "O tema é obrigatório") String tema
) {}