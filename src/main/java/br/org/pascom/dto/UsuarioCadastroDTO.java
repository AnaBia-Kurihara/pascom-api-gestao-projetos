package br.org.pascom.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioCadastroDTO(
        @NotBlank(message = "O nome é obrigatório") String nome,
        @Email @NotBlank(message = "O e-mail é obrigatório") String email,
        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 6, message = "A senha precisa ter pelo menos 6 caracteres") String senha,
        String funcao,
        String disponibilidade,
        Integer avatarHue
) {}