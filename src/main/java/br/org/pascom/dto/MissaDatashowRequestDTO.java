package br.org.pascom.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record MissaDatashowRequestDTO(
        @NotNull(message = "A data é obrigatória") LocalDate data,
        LocalTime horario,
        String local,
        String listaMusicas,
        String avisosOutrasPastorais,
        Long responsavelMontagemId,
        Long responsavelPassarId
) {}
