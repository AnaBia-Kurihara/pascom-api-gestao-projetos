package br.org.pascom.dto;

import br.org.pascom.model.Transmissao;
import br.org.pascom.model.enums.EtapaTransmissao;
import br.org.pascom.model.enums.TipoTransmissao;

import java.time.LocalDate;
import java.time.LocalTime;

public record TransmissaoResponseDTO(
        Long id,
        LocalDate data,
        LocalTime horario,
        String titulo,
        TipoTransmissao tipo,
        EtapaTransmissao etapa,
        String temaLiturgico,
        String link,
        String capaLink,
        UsuarioResumoDTO responsavel
) {
    public static TransmissaoResponseDTO from(Transmissao t) {
        return new TransmissaoResponseDTO(
                t.getId(), t.getData(), t.getHorario(), t.getTitulo(), t.getTipo(), t.getEtapa(),
                t.getTemaLiturgico(), t.getLink(), t.getCapaLink(),
                UsuarioResponseDTO.resumo(t.getResponsavel())
        );
    }
}
