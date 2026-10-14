package br.org.pascom.dto;

import br.org.pascom.model.enums.Setor;
import br.org.pascom.model.enums.SlotTipo;

public record SlotEscalaDTO(
        Long id,
        String rotulo,
        SlotTipo tipo,
        Long voluntarioId,
        String voluntarioNome,
        /** Setor responsável pela função; null = função geral. */
        Setor setor
) {}
