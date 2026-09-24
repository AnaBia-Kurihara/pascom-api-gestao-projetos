package br.org.pascom.dto;

import br.org.pascom.model.MissaDatashow;
import br.org.pascom.model.enums.EtapaDatashow;

import java.time.LocalDate;
import java.time.LocalTime;

public record MissaDatashowResponseDTO(
        Long id,
        LocalDate data,
        LocalTime horario,
        String local,
        EtapaDatashow etapa,
        String listaMusicas,
        String avisosOutrasPastorais,
        UsuarioResumoDTO responsavelMontagem,
        UsuarioResumoDTO responsavelPassar
) {
    public static MissaDatashowResponseDTO from(MissaDatashow m) {
        return new MissaDatashowResponseDTO(
                m.getId(), m.getData(), m.getHorario(), m.getLocal(), m.getEtapa(),
                m.getListaMusicas(), m.getAvisosOutrasPastorais(),
                UsuarioResponseDTO.resumo(m.getResponsavelMontagem()),
                UsuarioResponseDTO.resumo(m.getResponsavelPassar())
        );
    }
}
