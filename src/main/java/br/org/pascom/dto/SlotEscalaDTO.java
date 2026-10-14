package br.org.pascom.dto;

import br.org.pascom.model.enums.Setor;
import br.org.pascom.model.enums.SlotTipo;

import java.util.List;

public record SlotEscalaDTO(
        Long id,
        String rotulo,
        SlotTipo tipo,
        Long voluntarioId,
        String voluntarioNome,
        /** Setor responsável pela função; null = função geral. */
        Setor setor,
        /** A escala deste setor neste mês já foi publicada pela coordenação (função geral: sempre true). */
        boolean publicada,
        /**
         * true quando quem está vendo não pode saber quem foi escalado (escala em montagem e a pessoa
         * não coordena o setor). Nesse caso voluntarioId/voluntarioNome vêm sempre null.
         */
        boolean oculto,
        /** Quem está vendo já se candidatou ("Quero servir") a esta função. */
        boolean euMeCandidatei,
        /** Candidatos à função: só pra quem coordena o setor; null pros demais. */
        List<UsuarioResumoDTO> candidatos
) {}
