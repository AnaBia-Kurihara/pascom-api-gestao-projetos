package br.org.pascom.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record EventoRequestDTO(
        @NotBlank(message = "O título do evento é obrigatório") String titulo,
        @NotNull(message = "A data é obrigatória") LocalDate data,
        @NotNull(message = "O horário é obrigatório") LocalTime horario,
        @NotBlank(message = "O local é obrigatório") String local,
        Boolean eventoGrande,
        @Valid List<SlotEscalaRequestDTO> slots
) {}