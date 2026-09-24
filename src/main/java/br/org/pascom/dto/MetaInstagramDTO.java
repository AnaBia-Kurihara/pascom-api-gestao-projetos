package br.org.pascom.dto;

import br.org.pascom.model.MetaInstagram;
import br.org.pascom.model.enums.TipoMetaInstagram;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MetaInstagramDTO(
        Long id,
        String titulo,
        String descricao,
        TipoMetaInstagram metrica,
        Integer valorAlvo,
        double valorAtual,
        int progresso,
        LocalDate dataAlvo,
        Boolean concluida,
        String criadoPorNome,
        LocalDateTime criadoEm
) {
    public static MetaInstagramDTO from(MetaInstagram m, double valorAtual) {
        int progresso = m.getValorAlvo() > 0
                ? (int) Math.min(100, Math.round((valorAtual / m.getValorAlvo()) * 100))
                : 0;
        return new MetaInstagramDTO(
                m.getId(), m.getTitulo(), m.getDescricao(), m.getMetrica(), m.getValorAlvo(),
                valorAtual, progresso, m.getDataAlvo(), m.getConcluida(),
                m.getCriadoPor().getNome(), m.getCriadoEm()
        );
    }
}
