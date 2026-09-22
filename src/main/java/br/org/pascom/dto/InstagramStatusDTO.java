package br.org.pascom.dto;

import java.time.LocalDateTime;

public record InstagramStatusDTO(
        boolean conectado,
        String username,
        LocalDateTime conectadoEm,
        LocalDateTime tokenExpiraEm
) {
    public static InstagramStatusDTO desconectado() {
        return new InstagramStatusDTO(false, null, null, null);
    }
}
