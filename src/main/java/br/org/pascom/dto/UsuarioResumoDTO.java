package br.org.pascom.dto;

import br.org.pascom.model.enums.Role;
import br.org.pascom.model.enums.Setor;

public record UsuarioResumoDTO(
        Long id,
        String nome,
        String funcao,
        Role role,
        Setor setor,
        Integer avatarHue
) {}
