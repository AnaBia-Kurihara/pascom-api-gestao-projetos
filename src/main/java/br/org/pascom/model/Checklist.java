package br.org.pascom.model;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Checklist {

    private boolean textoRevisado;
    private boolean doutrinaConferida;
    private boolean imagemAutorizada;
    private boolean musicaLivre;
    private boolean legendaEDatasDefinidas;

    public boolean isCompleto() {
        return textoRevisado && doutrinaConferida && imagemAutorizada && musicaLivre && legendaEDatasDefinidas;
    }
}