package br.org.pascom.dto;

public record InstagramPostDTO(
        String id,
        String permalink,
        String legenda,
        String timestamp,
        String tipoMidia,
        Integer curtidas,
        Integer comentarios
) {}
