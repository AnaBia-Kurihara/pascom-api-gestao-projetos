package br.org.pascom.dto;

import br.org.pascom.model.enums.MotivoExclusao;
import jakarta.validation.constraints.NotNull;

public record ExclusaoRequestDTO(
        @NotNull(message = "O motivo da exclusão é obrigatório") MotivoExclusao motivo,
        String detalhe
) {}
