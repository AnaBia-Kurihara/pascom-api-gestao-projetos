package br.org.pascom.dto;

import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Role;
import br.org.pascom.model.enums.Setor;

public record UsuarioResponseDTO(
        Long id,
        String nome,
        String email,
        Role role,
        Setor setor,
        String funcao,
        String disponibilidade,
        Integer avatarHue
) {
    public static UsuarioResponseDTO from(Usuario u) {
        return new UsuarioResponseDTO(
                u.getId(), u.getNome(), u.getEmail(), u.getRole(), u.getSetor(),
                u.getFuncao(), u.getDisponibilidade(), u.getAvatarHue()
        );
    }

    public static UsuarioResumoDTO resumo(Usuario u) {
        if (u == null) return null;
        return new UsuarioResumoDTO(u.getId(), u.getNome(), u.getFuncao(), u.getRole(), u.getSetor(), u.getAvatarHue());
    }
}