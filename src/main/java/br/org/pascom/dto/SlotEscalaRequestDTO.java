package br.org.pascom.dto;

import br.org.pascom.model.enums.SlotTipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SlotEscalaRequestDTO(
        @NotBlank(message = "O rótulo do slot é obrigatório") String rotulo,
        @NotNull(message = "O tipo do slot é obrigatório") SlotTipo tipo
) {}