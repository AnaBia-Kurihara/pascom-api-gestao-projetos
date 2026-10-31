package br.org.pascom.service;

import br.org.pascom.model.enums.Setor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** O ciclo mensal da escala com datas de verdade do calendário de 2026/2027. */
class CicloEscalaTest {

    private static LocalDate d(int ano, int mes, int dia) {
        return LocalDate.of(ano, mes, dia);
    }

    @Test
    void sabadoAntesDoDomingoDia1EntraNoMesSeguinte() {
        // 31/10/2026 é sábado e 01/11/2026 é domingo: o fim de semana é da escala de novembro.
        assertThat(CicloEscala.mesDaEscala(d(2026, 10, 31))).isEqualTo(YearMonth.of(2026, 11));
        assertThat(CicloEscala.mesDaEscala(d(2026, 11, 1))).isEqualTo(YearMonth.of(2026, 11));
        assertThat(CicloEscala.mesDaEscala(d(2026, 10, 30))).isEqualTo(YearMonth.of(2026, 10));
        assertThat(CicloEscala.inicio(YearMonth.of(2026, 11))).isEqualTo(d(2026, 10, 31));
        assertThat(CicloEscala.fim(YearMonth.of(2026, 10))).isEqualTo(d(2026, 10, 30));
        // Mês comum: começa no dia 1 e termina no último dia.
        assertThat(CicloEscala.inicio(YearMonth.of(2026, 12))).isEqualTo(d(2026, 12, 1));
        assertThat(CicloEscala.fim(YearMonth.of(2026, 12))).isEqualTo(d(2026, 12, 31));
    }

    @Test
    void candidaturasAbremDia15EVaoAteODiaDaMissa() {
        // Dezembro/2026: abre 15/11; o prazo da montagem vai até 28/11 (29 e 30 são da coordenação).
        LocalDate missaDeDezembro = d(2026, 12, 6);
        assertThat(CicloEscala.candidaturaAberta(missaDeDezembro, d(2026, 11, 14))).isFalse(); // ainda não abriu
        assertThat(CicloEscala.candidaturaAberta(missaDeDezembro, d(2026, 11, 15))).isTrue();
        assertThat(CicloEscala.candidaturaAberta(missaDeDezembro, d(2026, 11, 29))).isTrue();  // depois do prazo: vagas que sobrarem
        assertThat(CicloEscala.candidaturaAberta(missaDeDezembro, d(2026, 12, 3))).isTrue();   // mês já começado
        assertThat(CicloEscala.candidaturaAberta(missaDeDezembro, d(2026, 12, 6))).isTrue();   // no próprio dia
        assertThat(CicloEscala.candidaturaAberta(missaDeDezembro, d(2026, 12, 7))).isFalse();  // já passou
        assertThat(CicloEscala.abreCandidaturas(YearMonth.of(2026, 12))).isEqualTo(d(2026, 11, 15));
        assertThat(CicloEscala.prazoCandidaturas(YearMonth.of(2026, 12))).isEqualTo(d(2026, 11, 29));
        // Janeiro/2027: dezembro tem 31 dias → prazo até 29/12 (30 e 31 da coordenação).
        assertThat(CicloEscala.prazoCandidaturas(YearMonth.of(2027, 1))).isEqualTo(d(2026, 12, 30));
        // Março/2027: fevereiro tem 28 dias → prazo até 26/02.
        assertThat(CicloEscala.prazoCandidaturas(YearMonth.of(2027, 3))).isEqualTo(d(2027, 2, 27));
    }

    @Test
    void novembroDe2026JaNasceuAbertoEVaiAteAMissa() {
        assertThat(CicloEscala.abreCandidaturas(YearMonth.of(2026, 11))).isNull();
        assertThat(CicloEscala.candidaturaAberta(d(2026, 11, 8), d(2026, 10, 27))).isTrue();
        assertThat(CicloEscala.candidaturaAberta(d(2026, 11, 8), d(2026, 10, 31))).isTrue(); // vaga que sobrou
        assertThat(CicloEscala.candidaturaAberta(d(2026, 11, 1), d(2026, 11, 2))).isFalse();
    }

    @Test
    void escalaFechaQuandoComecaEOutubroNuncaFecha() {
        assertThat(CicloEscala.fechado(YearMonth.of(2026, 11), d(2026, 10, 30))).isFalse();
        assertThat(CicloEscala.fechado(YearMonth.of(2026, 11), d(2026, 10, 31))).isTrue(); // começa no sábado
        assertThat(CicloEscala.fechado(YearMonth.of(2026, 10), d(2026, 10, 27))).isFalse();
        assertThat(CicloEscala.fechado(YearMonth.of(2026, 12), d(2026, 12, 1))).isTrue();
    }

    @Test
    void publicacaoAutomaticaNaVesperaDoInicio() {
        assertThat(CicloEscala.publicacaoAutomatica(YearMonth.of(2026, 11))).isEqualTo(d(2026, 10, 30));
        assertThat(CicloEscala.publicacaoAutomatica(YearMonth.of(2026, 12))).isEqualTo(d(2026, 11, 30));
    }

    @Test
    void sabado31DeOutubroTambemValePelaPublicacaoDeOutubro() {
        assertThat(CicloEscala.chavesPublicacao(d(2026, 10, 31), Setor.DATASHOW))
                .containsExactlyInAnyOrder("2026-11|DATASHOW", "2026-10|DATASHOW");
        // Daqui pra frente, o sábado dividido só vale pela publicação do mês seguinte (sigilo).
        assertThat(CicloEscala.chavesPublicacao(d(2027, 7, 31), Setor.DATASHOW)).containsExactly("2027-08|DATASHOW");
        assertThat(CicloEscala.mesDaEscala(d(2027, 7, 31))).isEqualTo(YearMonth.of(2027, 8));
    }
}
