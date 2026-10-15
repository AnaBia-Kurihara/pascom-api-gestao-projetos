package br.org.pascom.dto;

import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Role;
import br.org.pascom.model.enums.Setor;

import java.time.ZoneId;
import java.util.List;

public record UsuarioResponseDTO(
        Long id,
        String nome,
        String email,
        Role role,
        Setor setor,
        String funcao,
        String disponibilidade,
        Integer avatarHue,
        /** true pra COORDENADOR_GERAL ou admin — o front usa isso em vez de comparar e-mail. */
        boolean poderesCoordenadorGeral,
        /** Outros setores dos quais a pessoa também faz parte, além do principal. */
        List<Setor> setoresExtras,
        /**
         * Versão da foto de perfil (null = sem foto). O front busca a imagem em
         * GET /api/usuarios/{id}/foto?v=<versão>, então trocar a foto invalida o cache sozinho.
         */
        Long fotoVersao,
        /** Gestor(a) de escalas: mexe na escala de todos os setores (ver Usuario.gereTodasAsEscalas). */
        boolean gestorEscalas
) {
    public static UsuarioResponseDTO from(Usuario u) {
        return new UsuarioResponseDTO(
                u.getId(), u.getNome(), u.getEmail(), u.getRole(), u.getSetor(),
                u.getFuncao(), u.getDisponibilidade(), u.getAvatarHue(),
                u.temPoderesDeCoordenadorGeral(),
                u.getSetoresExtras().stream().sorted().toList(),
                u.getFotoAtualizadaEm() == null ? null
                        : u.getFotoAtualizadaEm().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                Boolean.TRUE.equals(u.getGestorEscalas())
        );
    }

    public static UsuarioResumoDTO resumo(Usuario u) {
        if (u == null) return null;
        return new UsuarioResumoDTO(u.getId(), u.getNome(), u.getFuncao(), u.getRole(), u.getSetor(), u.getAvatarHue());
    }
}