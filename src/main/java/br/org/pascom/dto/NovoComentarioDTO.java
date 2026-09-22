package br.org.pascom.dto;

import jakarta.validation.constraints.NotBlank;

public record NovoComentarioDTO(
        @NotBlank(message = "O comentário não pode ficar vazio") String texto
) {}
