package br.org.pascom.dto;

import br.org.pascom.model.enums.Setor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IdeiaRequestDTO(
        @NotBlank(message = "O título é obrigatório") String titulo,
        String descricao,
        @NotBlank(message = "O tema é obrigatório") String tema,
        @NotNull(message = "O setor é obrigatório") Setor setor
) {}
