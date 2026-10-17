package br.org.pascom.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liturgia do dia (leituras, salmo, evangelho, antífonas, orações, cor) pra quem monta o Holyrics,
 * vinda de uma API aberta da comunidade (api.liturgia.url). Fica em cache por 12h por data, pra
 * não chamar a API toda vez que alguém abre a tela. Se a API cair, devolve um erro amigável — a
 * pessoa ainda tem os links da CNBB e da Canção Nova na tela.
 */
@Service
public class LiturgiaService {

    private static final Duration VALIDADE_CACHE = Duration.ofHours(12);

    private record Cache(Map<String, Object> dados, Instant em) {}

    private final Map<LocalDate, Cache> cache = new ConcurrentHashMap<>();
    private final RestClient restClient;
    private final String url;

    public LiturgiaService(@Value("${api.liturgia.url}") String url) {
        this.url = url;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(8).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.restClient = RestClient.builder().requestFactory(f).build();
    }

    public Map<String, Object> doDia(LocalDate data) {
        Cache c = cache.get(data);
        if (c != null && c.em().plus(VALIDADE_CACHE).isAfter(Instant.now())) return c.dados();
        try {
            Map<String, Object> dados = restClient.get()
                    .uri(url + "?dia={d}&mes={m}&ano={a}", data.getDayOfMonth(), data.getMonthValue(), data.getYear())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (dados == null || dados.isEmpty()) throw new IllegalStateException("vazio");
            cache.put(data, new Cache(dados, Instant.now()));
            return dados;
        } catch (RestClientException | IllegalStateException e) {
            if (c != null) return c.dados(); // melhor a versão de algumas horas atrás do que nada
            throw new IllegalStateException("Não consegui buscar a liturgia agora. Tente de novo em alguns minutos "
                    + "ou use os links da CNBB/Canção Nova.");
        }
    }
}
