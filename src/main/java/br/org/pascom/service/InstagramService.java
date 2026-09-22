package br.org.pascom.service;

import br.org.pascom.dto.InstagramConectarDTO;
import br.org.pascom.dto.InstagramStatusDTO;
import br.org.pascom.dto.MetricaPostagemDTO;
import br.org.pascom.model.Cartao;
import br.org.pascom.model.ContaInstagram;
import br.org.pascom.model.MetricaPostagem;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Etapa;
import br.org.pascom.model.enums.Role;
import br.org.pascom.repository.CartaoRepository;
import br.org.pascom.repository.ContaInstagramRepository;
import br.org.pascom.repository.MetricaPostagemRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Integração com a Instagram Graph API (conta Business/Creator).
 *
 * ESTADO ATUAL: estrutura pronta (conexão, criptografia do token, coleta e histórico de
 * métricas), mas ainda não testada contra a API real — falta a autorização/App Review da
 * Meta para a conta do santuário. Assim que a conta estiver conectada (via
 * {@link #conectar}), basta chamar {@link #coletarMetricas} para validar a integração de
 * ponta a ponta; os nomes de métrica em {@link #METRICAS} podem precisar de ajuste conforme
 * o tipo de mídia (feed, reels ou carrossel) devolvido pela API nesse primeiro teste real.
 */
@Service
public class InstagramService {

    private static final String GRAPH_BASE_URL = "https://graph.facebook.com/v21.0";
    private static final String METRICAS = "likes,comments,saved,shares,reach";

    private final ContaInstagramRepository contaRepository;
    private final MetricaPostagemRepository metricaRepository;
    private final CartaoRepository cartaoRepository;
    private final CriptografiaService criptografiaService;
    private final RestClient restClient;

    public InstagramService(ContaInstagramRepository contaRepository, MetricaPostagemRepository metricaRepository,
                             CartaoRepository cartaoRepository, CriptografiaService criptografiaService) {
        this.contaRepository = contaRepository;
        this.metricaRepository = metricaRepository;
        this.cartaoRepository = cartaoRepository;
        this.criptografiaService = criptografiaService;
        this.restClient = RestClient.create();
    }

    public InstagramStatusDTO status() {
        return contaRepository.findById(ContaInstagram.ID_UNICO)
                .map(c -> new InstagramStatusDTO(true, c.getUsername(), c.getConectadoEm(), c.getTokenExpiraEm()))
                .orElse(InstagramStatusDTO.desconectado());
    }

    @Transactional
    public InstagramStatusDTO conectar(InstagramConectarDTO dados, Usuario solicitante) {
        exigirCoordenacao(solicitante);

        ContaInstagram conta = ContaInstagram.builder()
                .id(ContaInstagram.ID_UNICO)
                .instagramUserId(dados.instagramUserId())
                .username(dados.username())
                .accessTokenCriptografado(criptografiaService.encriptar(dados.accessToken()))
                .conectadoPor(solicitante)
                .conectadoEm(LocalDateTime.now())
                .tokenExpiraEm(dados.tokenExpiraEm())
                .build();

        contaRepository.save(conta);
        return status();
    }

    @Transactional
    public void desconectar(Usuario solicitante) {
        exigirCoordenacao(solicitante);
        contaRepository.deleteById(ContaInstagram.ID_UNICO);
    }

    @Transactional
    public MetricaPostagemDTO coletarMetricas(Long cartaoId) {
        Cartao cartao = cartaoRepository.findById(cartaoId)
                .orElseThrow(() -> new IllegalArgumentException("Cartão não encontrado."));

        if (cartao.getInstagramMediaId() == null || cartao.getInstagramMediaId().isBlank()) {
            throw new IllegalStateException("Este cartão ainda não está vinculado a uma publicação do Instagram.");
        }

        String token = tokenAtivo();
        Map<String, Integer> valores = buscarInsights(cartao.getInstagramMediaId(), token);

        MetricaPostagem metrica = MetricaPostagem.builder()
                .cartao(cartao)
                .coletadoEm(LocalDateTime.now())
                .curtidas(valores.get("likes"))
                .comentarios(valores.get("comments"))
                .salvamentos(valores.get("saved"))
                .compartilhamentos(valores.get("shares"))
                .alcance(valores.get("reach"))
                .build();

        return MetricaPostagemDTO.from(metricaRepository.save(metrica));
    }

    public List<MetricaPostagemDTO> historico(Long cartaoId) {
        return metricaRepository.findByCartaoIdOrderByColetadoEmAsc(cartaoId).stream()
                .map(MetricaPostagemDTO::from).toList();
    }

    /** Roda todo dia às 6h: coleta métricas de todo cartão publicado já vinculado a um post. */
    @Scheduled(cron = "0 0 6 * * *")
    public void coletarMetricasDiarias() {
        if (contaRepository.findById(ContaInstagram.ID_UNICO).isEmpty()) {
            return;
        }
        List<Cartao> publicados = cartaoRepository.findByEtapaAndInstagramMediaIdIsNotNull(Etapa.PUBLICADO);
        for (Cartao cartao : publicados) {
            try {
                coletarMetricas(cartao.getId());
            } catch (Exception ignorada) {
                // Uma falha isolada (ex.: post apagado) não pode interromper a coleta das demais.
            }
        }
    }

    private Map<String, Integer> buscarInsights(String mediaId, String token) {
        try {
            InsightsResponse resposta = restClient.get()
                    .uri(GRAPH_BASE_URL + "/{mediaId}/insights?metric={metricas}&access_token={token}",
                            mediaId, METRICAS, token)
                    .retrieve()
                    .body(InsightsResponse.class);

            if (resposta == null || resposta.data() == null) {
                return Map.of();
            }
            return resposta.data().stream()
                    .filter(item -> item.values() != null && !item.values().isEmpty())
                    .collect(Collectors.toMap(InsightItem::name, item -> item.values().get(0).value(), (a, b) -> a));
        } catch (RestClientException e) {
            throw new IllegalStateException("Não foi possível buscar as métricas no Instagram: " + e.getMessage());
        }
    }

    private String tokenAtivo() {
        ContaInstagram conta = contaRepository.findById(ContaInstagram.ID_UNICO)
                .orElseThrow(() -> new IllegalStateException("A conta do Instagram ainda não foi conectada."));
        return criptografiaService.decriptar(conta.getAccessTokenCriptografado());
    }

    private void exigirCoordenacao(Usuario usuario) {
        if (usuario.getRole() != Role.COORDENACAO) {
            throw new IllegalStateException("Apenas a Coordenação pode gerenciar a conexão com o Instagram.");
        }
    }

    private record InsightsResponse(List<InsightItem> data) {}
    private record InsightItem(String name, List<InsightValue> values) {}
    private record InsightValue(Integer value) {}
}
