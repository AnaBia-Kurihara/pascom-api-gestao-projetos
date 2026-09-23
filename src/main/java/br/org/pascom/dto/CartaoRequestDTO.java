package br.org.pascom.dto;

import br.org.pascom.model.Checklist;
import br.org.pascom.model.enums.Formato;
import br.org.pascom.model.enums.Setor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CartaoRequestDTO(
        @NotBlank(message = "O título é obrigatório") String titulo,
        @NotNull(message = "O formato é obrigatório") Formato formato,
        @NotBlank(message = "O tema é obrigatório") String tema,
        Long responsavelId,
        LocalDate prazoEntrega,
        LocalDate dataPublicacao,
        String roteiroNotas,
        Checklist checklist,
        @NotNull(message = "O setor é obrigatório") Setor setor
) {}
