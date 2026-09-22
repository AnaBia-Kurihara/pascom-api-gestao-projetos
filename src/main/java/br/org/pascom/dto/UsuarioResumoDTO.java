package br.org.pascom.dto;

import br.org.pascom.model.enums.Role;

public record UsuarioResumoDTO(
        Long id,
        String nome,
        String funcao,
        Role role,
        Integer avatarHue
) {}
