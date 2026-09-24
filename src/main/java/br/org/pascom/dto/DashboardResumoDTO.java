package br.org.pascom.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardResumoDTO(
        String periodo,
        LocalDate inicio,
        LocalDate fim,
        int totalPublicados,
        int totalVinculadosInstagram,
        long totalCurtidas,
        long totalComentarios,
        long totalSalvamentos,
        long totalCompartilhamentos,
        long totalAlcance,
        List<DashboardPostDTO> posts
) {}
