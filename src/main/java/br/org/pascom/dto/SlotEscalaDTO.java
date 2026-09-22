package br.org.pascom.dto;

import br.org.pascom.model.enums.SlotTipo;

public record SlotEscalaDTO(
        Long id,
        String rotulo,
        SlotTipo tipo,
        Long voluntarioId,
        String voluntarioNome
) {}