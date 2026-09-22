package br.org.pascom.dto;

import br.org.pascom.model.Comentario;

import java.time.LocalDateTime;

public record ComentarioResponseDTO(
        Long id,
        String texto,
        LocalDateTime criadoEm,
        UsuarioResumoDTO autor
) {
    public static ComentarioResponseDTO from(Comentario c) {
        return new ComentarioResponseDTO(
                c.getId(), c.getTexto(), c.getCriadoEm(), UsuarioResponseDTO.resumo(c.getAutor())
        );
    }
}
