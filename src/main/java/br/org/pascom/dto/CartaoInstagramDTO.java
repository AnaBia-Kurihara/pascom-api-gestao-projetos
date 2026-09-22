package br.org.pascom.dto;

import jakarta.validation.constraints.NotBlank;

public record CartaoInstagramDTO(
        @NotBlank(message = "O ID da mídia no Instagram é obrigatório") String instagramMediaId,
        String instagramPermalink
) {}
