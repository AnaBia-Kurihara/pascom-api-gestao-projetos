package br.org.pascom.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Leitura do site da Paulus, sem internet: as páginas em src/test/resources/paulus copiam a estrutura
 * real do site (com texto inventado). Se a Paulus mudar o layout, é aqui que se atualiza o modelo.
 */
class PaulusLiturgiaServiceTest {

    private static final String LINK = "https://www.paulus.com.br/portal/liturgia-diaria/teste/";
    private final PaulusLiturgiaService paulus = new PaulusLiturgiaService("http://nao-usado");

    @Test
    void domingoTrazCelebracaoCorAntifonaELeituras() {
        Map<String, Object> l = ler("domingo.html", LocalDate.of(2026, 10, 18));

        assertThat(l.get("liturgia")).isEqualTo("29º Domingo do Tempo Comum");
        assertThat(l.get("cor")).isEqualTo("Verde");
        assertThat(l.get("fonte")).isEqualTo("Paulus");
        assertThat(l.get("linkFonte")).isEqualTo(LINK);
        assertThat(antifonas(l).get("entrada")).isEqualTo("Antífona de entrada de teste (Sl 16,6.8).");

        Map<String, Object> p1 = primeira(l, "primeiraLeitura");
        assertThat(p1.get("referencia")).isEqualTo("Isaías 1,1-3");
        assertThat(p1.get("titulo")).isEqualTo("Leitura do livro do profeta Isaías");
        // sem os números dos versículos e sem o "Palavra do Senhor" do fim
        assertThat(p1.get("texto")).isEqualTo("Primeiro versículo de teste. Segundo versículo de teste.");
    }

    @Test
    void salmoSeparaRefraoEEstrofes() {
        Map<String, Object> salmo = primeira(ler("domingo.html", LocalDate.of(2026, 10, 18)), "salmo");

        assertThat(salmo.get("referencia")).isEqualTo("10(11)");
        assertThat(salmo.get("refrao")).isEqualTo("Refrão do salmo de teste!");
        assertThat(salmo.get("texto")).isEqualTo(
                "Primeira linha da estrofe,\nsegunda linha da estrofe.\n\nTerceira linha,\nquarta linha.");
    }

    @Test
    void evangelhoTemAclamacaoSeparada() {
        Map<String, Object> ev = primeira(ler("domingo.html", LocalDate.of(2026, 10, 18)), "evangelho");

        assertThat(ev.get("titulo")).isEqualTo("Proclamação do Evangelho de Jesus Cristo segundo Marcos");
        assertThat(ev.get("texto")).isEqualTo("Naquele tempo, Jesus disse uma frase de teste. E outra frase.");
        assertThat(ev.get("aclamacao")).isEqualTo("Aleluia, aleluia, aleluia.\nVersículo da aclamação,\nsegunda parte (Mc 1,1).");
    }

    @Test
    void notaDaFormaBreveNaoViraTituloDaLeitura() {
        Map<String, Object> p2 = primeira(ler("domingo.html", LocalDate.of(2026, 10, 18)), "segundaLeitura");

        assertThat(p2.get("titulo")).isEqualTo("Leitura da primeira carta de São Paulo aos Coríntios");
        assertThat((String) p2.get("texto")).startsWith("[Irmãos");
    }

    @Test
    void reflexaoNaoEntraNaMontagem() {
        Map<String, Object> l = ler("domingo.html", LocalDate.of(2026, 10, 18));
        assertThat(l.toString()).doesNotContain("Texto da reflexão");
    }

    @Test
    void memoriaComNomeECorNoMesmoParagrafo() {
        Map<String, Object> l = ler("memoria.html", LocalDate.of(2026, 10, 17));

        assertThat(l.get("liturgia")).isEqualTo("Santo de Teste – Bispo e Mártir");
        assertThat(l.get("cor")).isEqualTo("Vermelho");
        assertThat(antifonas(l).get("entrada")).isEqualTo("Antífona da memória (Gl 2,19s).");
        assertThat(leituras(l)).doesNotContainKey("segundaLeitura");
    }

    @Test
    void solenidadeComAntifonaEmItalicoEDiaPrimeiro() {
        Map<String, Object> l = ler("solenidade.html", LocalDate.of(2026, 11, 1));

        assertThat(l.get("liturgia")).isEqualTo("Epifania do Senhor");
        assertThat(l.get("cor")).isEqualTo("Branco");
        assertThat(antifonas(l).get("entrada")).isEqualTo("Antífona em itálico (Br 5,5).");
    }

    @Test
    void paginaDeOutroDiaEhRecusada() {
        // Se o sitemap apontar pra página errada, melhor cair na reserva do que montar a liturgia de outro dia.
        assertThatThrownBy(() -> ler("domingo.html", LocalDate.of(2026, 10, 25)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void paginaSemLeiturasEhRecusada() {
        assertThatThrownBy(() -> paulus.lerHtml("<html><div class='titulo-liturgia'>Dia 18</div><div id='texto'><p>nada</p></div></html>",
                LocalDate.of(2026, 10, 18), LINK)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sitemapCasaPeloNumeroDoDiaNoLink() {
        Map<LocalDate, String> p = PaulusLiturgiaService.indexarSitemap(recurso("sitemap.xml"));
        String b = "https://www.paulus.com.br/portal/liturgia-diaria/";

        assertThat(p.get(LocalDate.of(2026, 10, 18))).isEqualTo(b + "dia-18-domingo-14/");
        assertThat(p.get(LocalDate.of(2026, 10, 17))).isEqualTo(b + "17-sabado-13/");
        assertThat(p.get(LocalDate.of(2026, 11, 1))).isEqualTo(b + "1o-domingo-5/");
        assertThat(p.get(LocalDate.of(2027, 2, 28))).isEqualTo(b + "dia-28/");
        // a do dia 26 veio com data do dia 25 no sitemap: o dia 25 continua sendo a página do 25
        assertThat(p.get(LocalDate.of(2026, 12, 25))).isEqualTo(b + "25-sexta-feira-11/");
        assertThat(p.get(LocalDate.of(2026, 12, 26))).isEqualTo(b + "26-sabado-12/");
        assertThat(p).hasSize(6); // a página sem número de dia fica de fora
    }

    // ---------------------------------------------------------------- apoio

    private Map<String, Object> ler(String arquivo, LocalDate data) {
        return paulus.lerHtml(recurso(arquivo), data, LINK);
    }

    private static String recurso(String arquivo) {
        try (InputStream in = PaulusLiturgiaServiceTest.class.getResourceAsStream("/paulus/" + arquivo)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<Map<String, Object>>> leituras(Map<String, Object> l) {
        return (Map<String, List<Map<String, Object>>>) l.get("leituras");
    }

    private static Map<String, Object> primeira(Map<String, Object> l, String chave) {
        return leituras(l).get(chave).get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> antifonas(Map<String, Object> l) {
        return (Map<String, Object>) l.get("antifonas");
    }
}
