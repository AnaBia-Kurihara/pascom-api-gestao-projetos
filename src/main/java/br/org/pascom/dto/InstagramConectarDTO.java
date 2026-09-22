package br.org.pascom.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * Dados para vincular a conta Instagram Business do santuário.
 * Hoje é preenchido manualmente (token obtido no Meta for Developers); quando o fluxo
 * OAuth completo for implementado, isso passa a ser preenchido automaticamente no callback.
 */
public record InstagramConectarDTO(
        @NotBlank(message = "O ID da conta Instagram é obrigatório") String instagramUserId,
        @NotBlank(message = "O nome de usuário é obrigatório") String username,
        @NotBlank(message = "O access token é obrigatório") String accessToken,
        LocalDateTime tokenExpiraEm
) {}
