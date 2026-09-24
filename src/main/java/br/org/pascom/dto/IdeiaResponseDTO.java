package br.org.pascom.dto;

import br.org.pascom.model.Ideia;
import br.org.pascom.model.enums.MotivoExclusao;
import br.org.pascom.model.enums.Setor;

import java.time.LocalDateTime;
import java.util.Set;

public record IdeiaResponseDTO(
        Long id,
        String titulo,
        String descricao,
        String tema,
        Setor setor,
        Long autorId,
        String autorNome,
        Boolean adotada,
        Integer totalVotos,
        Set<Long> votantesIds,
        LocalDateTime excluidoEm,
        MotivoExclusao motivoExclusao,
        String detalheExclusao
) {
    public static IdeiaResponseDTO from(Ideia i) {
        return new IdeiaResponseDTO(
                i.getId(), i.getTitulo(), i.getDescricao(), i.getTema(), i.getSetor(),
                i.getAutor().getId(), i.getAutor().getNome(),
                i.getAdotada(), i.getVotantesIds().size(), i.getVotantesIds(),
                i.getExcluidoEm(), i.getMotivoExclusao(), i.getDetalheExclusao()
        );
    }
}
