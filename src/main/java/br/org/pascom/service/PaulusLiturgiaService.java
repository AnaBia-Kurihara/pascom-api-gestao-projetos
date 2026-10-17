package br.org.pascom.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê a liturgia do dia do site da Paulus (escolha da coordenação: é a fonte mais confiável, a CNBB
 * às vezes muda alguma coisa). O site não tem API: o sitemap das páginas de liturgia traz a data de
 * cada uma (a Paulus publica com meses de antecedência), e a página do dia tem uma estrutura fixa
 * (título da celebração, cor entre parênteses, antífona, e um bloco "subtitulo-liturgia" por leitura).
 * Devolve no mesmo formato da API da comunidade, pra o front não precisar saber de onde veio.
 */
@Service
public class PaulusLiturgiaService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Duration VALIDADE_SITEMAP = Duration.ofHours(6);
    private static final int TIMEOUT_MS = 15_000;
    private static final String AGENTE = "Mozilla/5.0 (compatible; PascomFatima/1.0; liturgia da montagem)";
    private static final Pattern ENTRADA_SITEMAP =
            Pattern.compile("<loc><!\\[CDATA\\[(.*?)]]></loc>\\s*<lastmod><!\\[CDATA\\[(.*?)]]>");
    /** Dia do mês no link: ".../dia-18-domingo-14/", ".../17-sabado-13/", ".../1o-domingo-5/", ".../dia-28/". */
    private static final Pattern DIA_NO_LINK = Pattern.compile("/liturgia-diaria/(?:dia-)?(\\d{1,2})o?(?:-|/)");
    private static final Pattern REF_NO_FIM = Pattern.compile("\\([^()]*\\d+,\\d+[^()]*\\)\\.?$");
    private static final Pattern FIM_DA_LEITURA = Pattern.compile("\\s*[–-]\\s*Palavra d[oa] (Senhor|salvação)\\.?\\s*$");

    private final String sitemapUrl;
    private volatile Map<LocalDate, String> paginas = Map.of();
    private volatile Instant sitemapLidoEm = Instant.EPOCH;

    public PaulusLiturgiaService(@Value("${api.liturgia.paulus-sitemap}") String sitemapUrl) {
        this.sitemapUrl = sitemapUrl;
    }

    /** Liturgia do dia, ou IllegalStateException se a Paulus não tiver (ainda) ou estiver fora do ar. */
    public Map<String, Object> doDia(LocalDate data) {
        String link = paginaDoDia(data);
        Document doc;
        try {
            doc = Jsoup.connect(link).userAgent(AGENTE).timeout(TIMEOUT_MS).get();
        } catch (IOException e) {
            throw new IllegalStateException("Paulus fora do ar: " + e.getMessage());
        }
        return ler(doc, data, link);
    }

    private String paginaDoDia(LocalDate data) {
        if (sitemapLidoEm.plus(VALIDADE_SITEMAP).isBefore(Instant.now()) || !paginas.containsKey(data)) {
            // Relê se venceu, ou se o dia não estava lá (a Paulus pode ter publicado depois), no máximo 1x/10min.
            if (sitemapLidoEm.plus(Duration.ofMinutes(10)).isBefore(Instant.now())) lerSitemap();
        }
        String link = paginas.get(data);
        if (link == null) throw new IllegalStateException("A Paulus ainda não publicou a liturgia de " + data);
        return link;
    }

    private synchronized void lerSitemap() {
        try {
            String xml = Jsoup.connect(sitemapUrl).userAgent(AGENTE).timeout(TIMEOUT_MS)
                    .ignoreContentType(true).maxBodySize(0).execute().body();
            Map<LocalDate, String> novo = new HashMap<>();
            Matcher m = ENTRADA_SITEMAP.matcher(xml);
            while (m.find()) {
                // A data de publicação de cada página costuma ser o próprio dia da liturgia (00h e pouco,
                // horário de Brasília), mas às vezes vem com um dia de diferença (ex.: a do dia 26/12
                // publicada no 25). Quem manda é o número do dia no link; a data só diz o mês/ano.
                Matcher d = DIA_NO_LINK.matcher(m.group(1));
                if (!d.find()) continue;
                int diaDoMes = Integer.parseInt(d.group(1));
                LocalDate publicada = OffsetDateTime.parse(m.group(2)).atZoneSameInstant(FUSO).toLocalDate();
                for (int delta = -3; delta <= 3; delta++) {
                    LocalDate dia = publicada.plusDays(delta);
                    if (dia.getDayOfMonth() == diaDoMes) {
                        // o que bate com a data exata ganha de um vizinho que também caiu nesse dia
                        if (delta == 0 || !novo.containsKey(dia)) novo.put(dia, m.group(1));
                        break;
                    }
                }
            }
            if (!novo.isEmpty()) paginas = novo;
        } catch (IOException | RuntimeException e) {
            // segue com o que já tinha; quem chamou cai na reserva se faltar o dia
        } finally {
            sitemapLidoEm = Instant.now();
        }
    }

    // ---------------------------------------------------------------- leitura da página

    private Map<String, Object> ler(Document doc, LocalDate data, String link) {
        Element texto = doc.selectFirst("#texto");
        Element titulo = doc.selectFirst(".titulo-liturgia");
        if (texto == null || titulo == null || !titulo.ownText().matches(".*(?<!\\d)" + data.getDayOfMonth() + "(?!\\d).*")) {
            throw new IllegalStateException("Página da Paulus em formato inesperado: " + link);
        }
        texto.select("sup").remove(); // números dos versículos

        List<String> nome = new ArrayList<>();
        String cor = null, antifonaEntrada = null, secao = null, referencia = null;
        List<String> paragrafos = new ArrayList<>();
        Map<String, List<Map<String, Object>>> leituras = new LinkedHashMap<>();
        String aclamacao = null;

        for (Element el : texto.children()) {
            if (el.hasClass("subtitulo-liturgia")) {
                aclamacao = fecharSecao(secao, referencia, paragrafos, leituras, aclamacao);
                String cab = el.ownText().replace(":", "").trim();
                if (cab.toLowerCase().startsWith("reflex")) { secao = null; break; }
                secao = cab;
                referencia = el.select("strong").text().trim();
                continue;
            }
            if (!el.tagName().equals("p")) continue;
            String t = limpar(el.text());
            if (t.isEmpty()) continue;
            if (secao != null && t.matches("^\\[A forma .*]$")) continue; // "[A forma breve está entre colchetes.]"
            if (secao != null) { paragrafos.add(secao.startsWith("Salmo") && el.selectFirst("strong") != null && paragrafos.isEmpty() ? "R:" + t : t); continue; }
            // Antes da primeira leitura: nome da celebração, cor "(verde, glória...)" (às vezes tudo num
            // parágrafo só, separado por <br>), depois a antífona de entrada e um comentário em itálico.
            if (cor == null) {
                for (String parte : el.html().split("(?i)<br\\s*/?>")) {
                    String linha = limpar(Jsoup.parse(parte).text());
                    if (linha.isEmpty()) continue;
                    if (linha.startsWith("(")) cor = corDe(linha);
                    else if (cor == null) nome.add(linha);
                }
            } else if (antifonaEntrada == null && (el.selectFirst("em") == null || REF_NO_FIM.matcher(t).find())) {
                // a antífona termina com a referência bíblica "(Sl 16,6.8)."; às vezes vem em itálico também
                antifonaEntrada = t;
            }
        }
        fecharSecao(secao, referencia, paragrafos, leituras, aclamacao);
        if (!leituras.containsKey("primeiraLeitura") || !leituras.containsKey("evangelho")) {
            throw new IllegalStateException("Não achei as leituras na página da Paulus: " + link);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("data", String.format("%02d/%02d/%d", data.getDayOfMonth(), data.getMonthValue(), data.getYear()));
        r.put("liturgia", nome.isEmpty() ? titulo.ownText().trim() : capitalizar(String.join(" – ", nome)));
        r.put("cor", cor);
        r.put("leituras", leituras);
        Map<String, Object> antifonas = new LinkedHashMap<>();
        antifonas.put("entrada", antifonaEntrada);
        r.put("antifonas", antifonas);
        r.put("oracoes", new LinkedHashMap<>());
        r.put("fonte", "Paulus");
        r.put("linkFonte", link);
        return r;
    }

    /** Guarda a seção que acabou de ser lida; devolve a aclamação ao Evangelho, se veio nela. */
    private String fecharSecao(String secao, String referencia, List<String> paragrafos,
                               Map<String, List<Map<String, Object>>> leituras, String aclamacao) {
        if (secao == null || paragrafos.isEmpty()) { paragrafos.clear(); return aclamacao; }
        String chave = secao.startsWith("Primeira") ? "primeiraLeitura" : secao.startsWith("Segunda") ? "segundaLeitura"
                : secao.startsWith("Salmo") ? "salmo" : secao.startsWith("Evangelho") ? "evangelho" : null;
        if (chave == null) { paragrafos.clear(); return aclamacao; }
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("referencia", referencia);
        if (chave.equals("salmo")) {
            String refrao = paragrafos.get(0).startsWith("R:") ? paragrafos.remove(0).substring(2) : null;
            List<String> estrofes = new ArrayList<>();
            for (String p : paragrafos) {
                estrofes.add(p.replaceFirst("^\\d+\\.\\s*", "").replaceFirst("\\s*[–-]\\s*R\\.?\\s*$", "")
                        .replaceAll("\\s*/\\s*", "\n"));
            }
            l.put("refrao", refrao);
            l.put("texto", String.join("\n\n", estrofes));
        } else {
            List<String> resto = new ArrayList<>(paragrafos);
            if (chave.equals("evangelho")) {
                // Antes da proclamação vêm o "Aleluia" e o versículo da aclamação.
                List<String> acl = new ArrayList<>();
                while (resto.size() > 1 && !resto.get(0).startsWith("Proclamação")) acl.add(resto.remove(0));
                if (!acl.isEmpty()) {
                    aclamacao = acl.stream().map(s -> s.replaceFirst("\\s*[–-]\\s*R\\.?\\s*$", "").replaceAll("\\s*/\\s*", "\n"))
                            .reduce((a, b) -> a + "\n" + b).orElse(null);
                }
            }
            String primeiro = resto.get(0);
            int traco = primeiro.indexOf(" – ");
            if (traco > 0 && traco < 140) {
                l.put("titulo", primeiro.substring(0, traco).trim());
                resto.set(0, primeiro.substring(traco + 3).trim());
            }
            l.put("texto", FIM_DA_LEITURA.matcher(String.join("\n", resto)).replaceFirst(""));
            if (chave.equals("evangelho") && aclamacao != null) l.put("aclamacao", aclamacao);
        }
        leituras.computeIfAbsent(chave, k -> new ArrayList<>()).add(l);
        paragrafos.clear();
        return aclamacao;
    }

    private static String limpar(String s) {
        return Parser.unescapeEntities(s, false).replace(' ', ' ').replaceAll("[ \\t]+", " ").trim();
    }

    private static String corDe(String t) {
        String c = t.replaceAll("^\\(\\s*", "").split("[,;)–-]")[0].trim();
        return c.isEmpty() ? null : Character.toUpperCase(c.charAt(0)) + c.substring(1).toLowerCase();
    }

    private static String capitalizar(String s) {
        // A Paulus escreve a celebração em CAIXA ALTA; no slide fica melhor "29º Domingo do Tempo Comum".
        if (!s.equals(s.toUpperCase())) return s;
        StringBuilder b = new StringBuilder();
        for (String p : s.toLowerCase().split(" ")) {
            if (b.length() > 0) b.append(' ');
            boolean menor = p.matches("d[aeo]s?|e|ou|em|n[ao]s?|[ao]s?");
            b.append(menor || p.isEmpty() ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1));
        }
        String r = b.toString();
        return r.isEmpty() ? r : Character.toUpperCase(r.charAt(0)) + r.substring(1);
    }
}
