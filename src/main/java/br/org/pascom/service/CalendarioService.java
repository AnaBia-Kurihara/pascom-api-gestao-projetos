package br.org.pascom.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Calendário da escala: quais são os domingos de cada mês.
 *
 * Calculado localmente com java.time (calendário gregoriano, o mesmo do Google Agenda) — é
 * uma conta exata, então não depende de chamada externa nem de chave/credencial do Google, e
 * nunca falha por rede fora do ar. Usa sempre o fuso de Brasília, pra virada do mês não
 * acontecer 3h antes por causa do servidor (Railway) estar em UTC.
 */
@Service
public class CalendarioService {

    public static final String FUSO = "America/Sao_Paulo";
    private static final ZoneId ZONA = ZoneId.of(FUSO);

    /**
     * Só nos testes automáticos (app.teste.hoje-dia): "hoje" vira esse dia do mês atual, pra as regras
     * de janela de candidatura (dia 11 a 20) não dependerem do dia em que o teste roda.
     */
    @org.springframework.beans.factory.annotation.Value("${app.teste.hoje-dia:0}")
    private int diaFixoDosTestes;

    public LocalDate hoje() {
        if (diaFixoDosTestes > 0) return YearMonth.now(ZONA).atDay(diaFixoDosTestes);
        return LocalDate.now(ZONA);
    }

    public YearMonth mesAtual() {
        return YearMonth.now(ZONA);
    }

    /** Todos os domingos do mês, em ordem (4 ou 5, dependendo do mês). */
    public List<LocalDate> domingosDoMes(YearMonth mes) {
        List<LocalDate> domingos = new ArrayList<>();
        LocalDate d = mes.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        while (!d.isAfter(mes.atEndOfMonth())) {
            domingos.add(d);
            d = d.plusWeeks(1);
        }
        return domingos;
    }

    /** Todos os dias do mês que caem no dia da semana informado (ex.: os sábados). */
    public List<LocalDate> diasDaSemanaDoMes(YearMonth mes, DayOfWeek dia) {
        List<LocalDate> dias = new ArrayList<>();
        LocalDate d = mes.atDay(1).with(TemporalAdjusters.nextOrSame(dia));
        while (YearMonth.from(d).equals(mes)) {
            dias.add(d);
            d = d.plusWeeks(1);
        }
        return dias;
    }

    public boolean ehDomingo(LocalDate data) {
        return data != null && data.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /** Converte "2026-10" em YearMonth; null/vazio = mês atual. */
    public YearMonth parseMes(String mes) {
        if (mes == null || mes.isBlank()) return mesAtual();
        try {
            return YearMonth.parse(mes.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Mês inválido. Use o formato AAAA-MM (ex.: 2026-10).");
        }
    }
}
