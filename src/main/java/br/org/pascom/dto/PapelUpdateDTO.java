package br.org.pascom.dto;

import br.org.pascom.model.enums.Role;
import br.org.pascom.model.enums.Setor;
import jakarta.validation.constraints.NotNull;

/** Coordenação Geral muda o papel (e o setor principal) de alguém da equipe. */
public record PapelUpdateDTO(
        @NotNull(message = "Escolha o papel") Role role,
        /** Obrigatório, menos pra COORDENADOR_GERAL (que coordena todos os setores). */
        Setor setor,
        /** Gestor(a) de escalas de todos os setores; null = não muda. */
        Boolean gestorEscalas
) {}
