package br.org.pascom.dto;

import br.org.pascom.model.MetricaPostagem;

import java.time.LocalDateTime;

public record MetricaPostagemDTO(
        Long id,
        LocalDateTime coletadoEm,
        Integer curtidas,
        Integer comentarios,
        Integer salvamentos,
        Integer compartilhamentos,
        Integer alcance,
        Integer impressoes
) {
    public static MetricaPostagemDTO from(MetricaPostagem m) {
        return new MetricaPostagemDTO(
                m.getId(), m.getColetadoEm(), m.getCurtidas(), m.getComentarios(),
                m.getSalvamentos(), m.getCompartilhamentos(), m.getAlcance(), m.getImpressoes()
        );
    }
}
