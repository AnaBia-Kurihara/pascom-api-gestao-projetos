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
 * Liturgia do dia (leituras, salmo, evangelho, antífonas, orações, cor) pra quem monta o Holyrics.
 * Fonte principal: a Paulus ({@link PaulusLiturgiaService}), escolhida pela coordenação. As orações
 * (coleta, oferendas, depois da comunhão) e a antífona da comunhão a Paulus não publica no site, então
 * vêm da API aberta da comunidade (api.liturgia.url), que também é a reserva se a Paulus cair.
 * Fica em cache por 12h por data, pra não buscar toda vez que alguém abre a tela.
 */
@Service
public class LiturgiaService {

    private static final Duration VALIDADE_CACHE = Duration.ofHours(12);

    private record Cache(Map<String, Object> dados, Instant em) {}

    private final Map<LocalDate, Cache> cache = new ConcurrentHashMap<>();
    private final RestClient restClient;
    private final String url;
    private final PaulusLiturgiaService paulus;

    public LiturgiaService(@Value("${api.liturgia.url}") String url, PaulusLiturgiaService paulus) {
        this.url = url;
        this.paulus = paulus;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(8).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.restClient = RestClient.builder().requestFactory(f).build();
    }

    public Map<String, Object> doDia(LocalDate data) {
        Cache c = cache.get(data);
        if (c != null && c.em().plus(VALIDADE_CACHE).isAfter(Instant.now())) return c.dados();
        Map<String, Object> dados;
        try {
            dados = paulus.doDia(data);
            completarComOracoes(dados, data);
        } catch (RuntimeException ePaulus) {
            try {
                dados = daComunidade(data);
                dados.put("fonte", "API da comunidade (a Paulus não respondeu)");
            } catch (RuntimeException e) {
                if (c != null) return c.dados(); // melhor a versão de algumas horas atrás do que nada
                throw new IllegalStateException("Não consegui buscar a liturgia agora. Tente de novo em alguns minutos "
                        + "ou use o site da Paulus.");
            }
        }
        cache.put(data, new Cache(dados, Instant.now()));
        return dados;
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
