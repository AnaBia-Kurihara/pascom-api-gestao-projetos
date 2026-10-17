package br.org.pascom.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liturgia do dia (leituras, salmo, evangelho, antífonas, orações, cor) pra quem monta o Holyrics.
 * Fonte principal: a Paulus ({@link PaulusLiturgiaService}), escolhida pela coordenação. As orações
 * (coleta, oferendas, depois da comunhão) e a antífona da comunhão a Paulus não publica no site, então
 * vêm da API aberta da comunidade (api.liturgia.url), que também é a reserva se a Paulus cair.
 * Fica em cache por 12h por data, pra não buscar toda vez que alguém abre a tela.
 */
@Service
public class LiturgiaService {

    private static final Duration VALIDADE_CACHE = Duration.ofHours(12);
    /** Veio da reserva porque a Paulus falhou: tenta a Paulus de novo logo. */
    private static final Duration VALIDADE_CACHE_RESERVA = Duration.ofHours(1);

    private record Cache(Map<String, Object> dados, Instant em, Duration validade) {
        boolean valido() { return em.plus(validade).isAfter(Instant.now()); }
    }

    private final Map<LocalDate, Cache> cache = new ConcurrentHashMap<>();
    private final RestClient restClient;
    private final String url;
    private final PaulusLiturgiaService paulus;

    private final CalendarioService calendarioService;

    public LiturgiaService(@Value("${api.liturgia.url}") String url, PaulusLiturgiaService paulus,
                           CalendarioService calendarioService) {
        this.url = url;
        this.paulus = paulus;
        this.calendarioService = calendarioService;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(8).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.restClient = RestClient.builder().requestFactory(f).build();
    }

    public Map<String, Object> doDia(LocalDate data) {
        Cache c = cache.get(data);
        if (c != null && c.valido()) return c.dados();
        Map<String, Object> dados;
        Duration validade = VALIDADE_CACHE;
        try {
            dados = paulus.doDia(data);
            completarComOracoes(dados, data);
        } catch (RuntimeException ePaulus) {
            validade = VALIDADE_CACHE_RESERVA;
            try {
                dados = daComunidade(data);
                dados.put("fonte", "API da comunidade (a Paulus não respondeu)");
            } catch (RuntimeException e) {
                if (c != null) return c.dados(); // melhor a versão de algumas horas atrás do que nada
                throw new IllegalStateException("Não consegui buscar a liturgia agora. Tente de novo em alguns minutos "
                        + "ou use o site da Paulus.");
            }
        }
        cache.put(data, new Cache(dados, Instant.now(), validade));
        return dados;
    }

    /**
     * A cada 6h (e 1 min depois de subir) busca antes a liturgia dos próximos 6 domingos, os mesmos
     * da tela de Montagem: assim quem abre a tela recebe do cache, sem esperar o site da Paulus.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 6 * 60 * 60 * 1000)
    public void buscarProximosDomingos() {
        cache.keySet().removeIf(d -> d.isBefore(calendarioService.hoje().minusDays(7)));
        LocalDate domingo = calendarioService.hoje().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        for (int i = 0; i < 6; i++) {
            try {
                doDia(domingo.plusWeeks(i));
            } catch (RuntimeException e) {
                // tenta de novo na próxima rodada ou quando alguém abrir a tela
            }
        }
    }

    /** Orações e antífona da comunhão, que a Paulus não publica no site. Se a API falhar, fica sem. */
    @SuppressWarnings("unchecked")
    private void completarComOracoes(Map<String, Object> dados, LocalDate data) {
        try {
            Map<String, Object> outra = daComunidade(data);
            if (outra.get("oracoes") instanceof Map<?, ?> o) dados.put("oracoes", o);
            if (outra.get("antifonas") instanceof Map<?, ?> a && dados.get("antifonas") instanceof Map<?, ?> nossa) {
                Map<String, Object> an = (Map<String, Object>) nossa;
                Object comunhao = a.get("comunhao");
                if (comunhao != null) an.put("comunhao", comunhao);
                if (an.get("entrada") == null) an.put("entrada", a.get("entrada"));
            }
        } catch (RuntimeException e) {
            // segue só com o que veio da Paulus
        }
    }

    private Map<String, Object> daComunidade(LocalDate data) {
        try {
            Map<String, Object> dados = restClient.get()
                    .uri(url + "?dia={d}&mes={m}&ano={a}", data.getDayOfMonth(), data.getMonthValue(), data.getYear())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (dados == null || dados.isEmpty()) throw new IllegalStateException("vazio");
            return new java.util.LinkedHashMap<>(dados);
        } catch (RestClientException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }
}
