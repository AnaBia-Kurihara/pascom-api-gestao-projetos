package br.org.pascom.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardAutoResumoDTO(
        String periodo,
        LocalDate inicio,
        LocalDate fim,
        int totalPosts,
        long totalCurtidas,
        long totalComentarios,
        long totalSalvamentos,
        long totalCompartilhamentos,
        long totalAlcance,
        double mediaCurtidas,
        List<PostagemInstagramDTO> posts
) {}
