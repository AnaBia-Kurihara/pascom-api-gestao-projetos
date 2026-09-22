package br.org.pascom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record EventoDTO(
        Long id,
        @NotBlank String titulo,
        @NotNull LocalDate data,
        @NotNull LocalTime horario,
        @NotBlank String local,
        Boolean eventoGrande,
        List<SlotEscalaDTO> slots
) {}