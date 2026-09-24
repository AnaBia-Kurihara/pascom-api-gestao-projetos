package br.org.pascom.dto;

import br.org.pascom.model.enums.Formato;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DashboardPostDTO(
        Long cartaoId,
        String titulo,
        Formato formato,
        LocalDate dataPublicacao,
        String instagramPermalink,
        boolean vinculadoInstagram,
        Integer curtidas,
        Integer comentarios,
        Integer salvamentos,
        Integer compartilhamentos,
        Integer alcance,
        LocalDateTime metricasColetadasEm
) {}
