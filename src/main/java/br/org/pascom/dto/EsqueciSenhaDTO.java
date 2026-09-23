package br.org.pascom.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EsqueciSenhaDTO(
        @Email @NotBlank(message = "O e-mail é obrigatório") String email
) {}
