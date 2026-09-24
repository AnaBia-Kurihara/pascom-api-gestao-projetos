package br.org.pascom.dto;

import java.time.LocalDateTime;

public record PostagemInstagramDTO(
        Long id,
        String instagramMediaId,
        String permalink,
        String legenda,
        String tipoMidia,
        LocalDateTime publicadoEm,
        Integer curtidas,
        Integer comentarios,
        Integer salvamentos,
        Integer compartilhamentos,
        Integer alcance,
        LocalDateTime metricasColetadasEm
) {}
