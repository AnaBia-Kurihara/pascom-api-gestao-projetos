package br.org.pascom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedefinirSenhaDTO(
        @NotBlank(message = "Token inválido") String token,
        @NotBlank(message = "A nova senha é obrigatória")
        @Size(min = 6, message = "A senha precisa ter pelo menos 6 caracteres") String novaSenha
) {}
