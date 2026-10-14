package br.org.pascom.dto;

import br.org.pascom.model.enums.Setor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Escala completa de um mês: junta a escala de todos os setores, evento por evento
 * (os domingos da Missa das 10h e qualquer evento extra marcado pela coordenação).
 */
public record EscalaCompletaDTO(
        String mes,
        /** null = todos os setores juntos; preenchido = escala só desse setor (+ funções gerais). */
        Setor setor,
        String nomeSetor,
        List<LocalDate> domingos,
        List<EventoEscala> eventos
) {
    public record EventoEscala(
            Long id,
            String titulo,
            LocalDate data,
            LocalTime horario,
            String local,
            boolean eventoGrande,
            boolean domingo,
            int vagasAbertas,
            List<GrupoSetor> setores
    ) {}

    /** @param setor null = funções gerais, abertas a qualquer setor */
    public record GrupoSetor(Setor setor, String nomeSetor, List<SlotEscalaDTO> funcoes) {}
}
