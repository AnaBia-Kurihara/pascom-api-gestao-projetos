package br.org.pascom.dto;

import br.org.pascom.model.Cartao;
import br.org.pascom.model.Checklist;
import br.org.pascom.model.enums.Etapa;
import br.org.pascom.model.enums.Formato;

import java.time.LocalDate;
import java.util.List;

public record CartaoResponseDTO(
        Long id,
        String titulo,
        Formato formato,
        String tema,
        Etapa etapa,
        UsuarioResumoDTO responsavel,
        LocalDate prazoEntrega,
        LocalDate dataPublicacao,
        String roteiroNotas,
        String instagramMediaId,
        String instagramPermalink,
        Checklist checklist,
        List<ComentarioResponseDTO> comentarios
) {
    public static CartaoResponseDTO from(Cartao c) {
        return new CartaoResponseDTO(
                c.getId(), c.getTitulo(), c.getFormato(), c.getTema(), c.getEtapa(),
                UsuarioResponseDTO.resumo(c.getResponsavel()),
                c.getPrazoEntrega(), c.getDataPublicacao(), c.getRoteiroNotas(),
                c.getInstagramMediaId(), c.getInstagramPermalink(),
                c.getChecklist(),
                c.getComentarios().stream().map(ComentarioResponseDTO::from).toList()
        );
    }
}