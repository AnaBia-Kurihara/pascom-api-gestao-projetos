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
        /** null = todos ou mais de um setor (ver setores); preenchido = escala só desse setor (+ funções gerais). */
        Setor setor,
        /** Nome do(s) setor(es) escolhido(s): "Datashow", "Holyrics + Datashow"; null = todos. */
        String nomeSetor,
        List<LocalDate> domingos,
        List<EventoEscala> eventos,
        /** Período da grade (a escala do mês, ou o que a pessoa escolheu ao exportar). */
        LocalDate inicio,
        LocalDate fim,
        /** Setores escolhidos, juntos na mesma grade (vazio = todos). */
        List<Setor> setores,
        /** Pronto pro título: "NOVEMBRO/2026" ou "31/10 A 15/11/2026". */
        String rotuloPeriodo
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
