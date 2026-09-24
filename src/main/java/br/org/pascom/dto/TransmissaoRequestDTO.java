package br.org.pascom.dto;

import br.org.pascom.model.enums.TipoTransmissao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record TransmissaoRequestDTO(
        @NotNull(message = "A data é obrigatória") LocalDate data,
        LocalTime horario,
        @NotBlank(message = "O título é obrigatório") String titulo,
        @NotNull(message = "O tipo é obrigatório") TipoTransmissao tipo,
        String temaLiturgico,
        String link,
        String capaLink,
        Long responsavelId
) {}
