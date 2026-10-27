package br.org.pascom.dto;

import br.org.pascom.model.enums.MotivoExclusao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record EventoDTO(
        Long id,
        @NotBlank String titulo,
        @NotNull LocalDate data,
        @NotNull LocalTime horario,
        @NotBlank String local,
        Boolean eventoGrande,
        List<SlotEscalaDTO> slots,
        LocalDateTime excluidoEm,
        MotivoExclusao motivoExclusao,
        String detalheExclusao,
        /** Mês da escala (o sábado antes de um domingo dia 1º entra no mês seguinte). Ver CicloEscala. */
        String mesEscala,
        /** Hoje dá pra se candidatar ("Quero servir") nas funções desse evento. */
        boolean candidaturaAberta,
        /** A escala desse mês já começou: fechada pra equipe. */
        boolean fechado,
        /** Dia em que as candidaturas abrem (null = já nasceu aberta) e em que fecham. */
        LocalDate candidaturasAbrem,
        LocalDate candidaturasFecham
) {}