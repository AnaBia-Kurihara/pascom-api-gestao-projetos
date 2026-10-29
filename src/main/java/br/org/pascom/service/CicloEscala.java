package br.org.pascom.service;

import br.org.pascom.model.enums.Setor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * As datas do ciclo mensal da escala, num lugar só (o servidor manda o resultado pronto pro front,
 * em cada evento):
 * <ul>
 *   <li><b>Mês da escala</b>: o mês do dia, exceto o sábado cujo domingo já é dia 1º, que entra na
 *       escala do mês seguinte junto com o domingo (ex.: sábado 31/10 + domingo 01/11 → novembro).
 *       A missa de sábado à tarde já é a do domingo, e a montagem do Holyrics é do fim de semana.</li>
 *   <li><b>Candidaturas</b> ("Quero servir") do mês M: abrem no dia 15 do mês anterior e vão até 3 dias
 *       antes do fim dele; os <b>2 últimos dias do mês</b> são da coordenação, pra montar e publicar
 *       (mês de 31 dias: candidaturas 15 a 29, coordenação 30 e 31; de 30 dias: 15 a 28, coordenação
 *       29 e 30; fevereiro de 28: 15 a 26, coordenação 27 e 28). Pedido da coordenação em out/2026,
 *       porque muita gente depende da escala de outras pastorais pra se organizar (antes era 11 a 20).
 *       Vale a partir de dezembro/2026; novembro/2026 ficou com a regra antiga (aberto até começar).</li>
 *   <li><b>Fechado</b>: quando a escala do mês começa, ela fecha pra equipe (sem candidatura, desistência
 *       nem sair sozinho). Outubro/2026, quando o sistema começou, nunca fecha.</li>
 *   <li><b>Publicação automática</b>: na véspera do primeiro dia da escala, se ninguém publicou.</li>
 * </ul>
 */
public final class CicloEscala {

    /** Primeiro mês em que o fechamento vale (o sistema começou no meio de outubro de 2026). */
    public static final YearMonth PRIMEIRO_MES_COM_FECHAMENTO = YearMonth.of(2026, 11);
    /** Primeiro mês com a janela de candidaturas (dia 15 até os 2 últimos dias do mês anterior). */
    public static final YearMonth PRIMEIRO_MES_COM_JANELA = YearMonth.of(2026, 12);
    public static final int DIA_ABRE_CANDIDATURAS = 15;
    /** Os últimos dias do mês anterior que ficam só pra coordenação montar e publicar. */
    public static final int DIAS_DA_COORDENACAO = 2;
    /** Antes disso o sábado dividido ainda estava na escala do próprio mês (já publicada). */
    private static final LocalDate INICIO_REGRA_DO_SABADO = LocalDate.of(2026, 11, 1);

    private CicloEscala() {}

    /** Em qual escala mensal cai essa data. */
    public static YearMonth mesDaEscala(LocalDate d) {
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY && d.plusDays(1).getDayOfMonth() == 1) {
            return YearMonth.from(d.plusDays(1));
        }
        return YearMonth.from(d);
    }

    /** Primeiro dia da escala do mês (o sábado anterior, quando o dia 1º é domingo). */
    public static LocalDate inicio(YearMonth m) {
        LocalDate dia1 = m.atDay(1);
        return dia1.getDayOfWeek() == DayOfWeek.SUNDAY ? dia1.minusDays(1) : dia1;
    }

    /** Último dia da escala do mês (sem o último sábado, se ele for da escala do mês seguinte). */
    public static LocalDate fim(YearMonth m) {
        LocalDate ultimo = m.atEndOfMonth();
        return ultimo.getDayOfWeek() == DayOfWeek.SATURDAY ? ultimo.minusDays(1) : ultimo;
    }

    /** Quando as candidaturas do mês abrem; null = já nasce aberta (meses da regra antiga). */
    public static LocalDate abreCandidaturas(YearMonth m) {
        return m.isBefore(PRIMEIRO_MES_COM_JANELA) ? null : m.minusMonths(1).atDay(DIA_ABRE_CANDIDATURAS);
    }

    /** A partir desse dia não dá mais pra se candidatar (só a coordenação mexe). */
    public static LocalDate fechaCandidaturas(YearMonth m) {
        return m.isBefore(PRIMEIRO_MES_COM_JANELA) ? inicio(m)
                : m.minusMonths(1).atEndOfMonth().minusDays(DIAS_DA_COORDENACAO - 1);
    }

    /** A escala do mês já começou: fechada pra equipe. */
    public static boolean fechado(YearMonth m, LocalDate hoje) {
        return !m.isBefore(PRIMEIRO_MES_COM_FECHAMENTO) && !hoje.isBefore(inicio(m));
    }

    public static boolean fechado(LocalDate data, LocalDate hoje) {
        return fechado(mesDaEscala(data), hoje);
    }

    /** Dá pra tocar em "Quero servir" (ou desistir) numa função desse dia, hoje? */
    public static boolean candidaturaAberta(LocalDate data, LocalDate hoje) {
        YearMonth m = mesDaEscala(data);
        if (fechado(m, hoje)) return false;
        LocalDate abre = abreCandidaturas(m);
        return (abre == null || !hoje.isBefore(abre)) && hoje.isBefore(fechaCandidaturas(m));
    }

    /** Dia em que o sistema publica sozinho a escala do mês, se ninguém publicou: a véspera do início. */
    public static LocalDate publicacaoAutomatica(YearMonth m) {
        return inicio(m).minusDays(1);
    }

    /**
     * Chaves "AAAA-MM|SETOR" que contam como "publicada" pra uma função desse dia. Normalmente só o
     * mês da escala; o sábado 31/10/2026, que já estava na escala de outubro publicada antes da regra
     * do sábado existir, também vale pela publicação de outubro (senão quem já estava escalado nele
     * sumiria da escala até novembro ser publicado).
     */
    public static Set<String> chavesPublicacao(LocalDate data, Setor setor) {
        Set<String> r = new LinkedHashSet<>();
        r.add(mesDaEscala(data) + "|" + setor.name());
        if (data.isBefore(INICIO_REGRA_DO_SABADO)) r.add(YearMonth.from(data) + "|" + setor.name());
        return r;
    }
}
