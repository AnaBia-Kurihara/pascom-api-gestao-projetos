package br.org.pascom.service;

import br.org.pascom.dto.DashboardPostDTO;
import br.org.pascom.dto.DashboardResumoDTO;
import br.org.pascom.dto.InstagramConectarDTO;
import br.org.pascom.dto.InstagramPostDTO;
import br.org.pascom.dto.InstagramStatusDTO;
import br.org.pascom.dto.MetricaPostagemDTO;
import br.org.pascom.model.Cartao;
import br.org.pascom.model.ContaInstagram;
import br.org.pascom.model.MetricaPostagem;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Etapa;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.repository.CartaoRepository;
import br.org.pascom.repository.ContaInstagramRepository;
import br.org.pascom.repository.MetricaPostagemRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Integração com a Instagram Graph API, via "Instagram API with Instagram Login"
 * (Business Login for Instagram — não exige Página do Facebook vinculada).
 *
 * Validada de ponta a ponta contra a conta real do santuário: conexão, criptografia do
 * token, e coleta de métricas de um post real (curtidas/alcance batendo com o valor real
 * do Instagram). Os tokens desse fluxo começam com "IGAA" e só funcionam contra o host
 * graph.instagram.com — graph.facebook.com devolve "Cannot parse access token" pra eles.
 */
@Service
public class InstagramService {

    // graph.facebook.com NÃO funciona para tokens gerados pelo fluxo "Instagram Login"
    // (tokens que começam com "IGAA") — eles exigem graph.instagram.com. Confirmado em
    // teste real: "Invalid OAuth access token - Cannot parse access token" com o host errado.
    private static final String GRAPH_BASE_URL = "https://graph.instagram.com/v21.0";
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

    /** Lista os posts recentes da conta conectada, pra facilitar escolher qual vincular a um cartão. */
    public List<InstagramPostDTO> listarPostsRecentes() {
        ContaInstagram conta = contaRepository.findById(ContaInstagram.ID_UNICO)
                .orElseThrow(() -> new IllegalStateException("A conta do Instagram ainda não foi conectada."));
        String token = criptografiaService.decriptar(conta.getAccessTokenCriptografado());

        try {
            MediaResponse resposta = restClient.get()
                    .uri(GRAPH_BASE_URL + "/{userId}/media?fields=id,caption,media_type,permalink,timestamp,like_count,comments_count&limit=25&access_token={token}",
                            conta.getInstagramUserId(), token)
                    .retrieve()
                    .body(MediaResponse.class);

            if (resposta == null || resposta.data() == null) {
                return List.of();
            }
            return resposta.data().stream()
                    .map(m -> new InstagramPostDTO(m.id(), m.permalink(), m.caption(), m.timestamp(), m.media_type(), m.like_count(), m.comments_count()))
                    .toList();
        } catch (RestClientException e) {
            throw new IllegalStateException("Não foi possível buscar os posts no Instagram: " + e.getMessage());
        }
    }

    /** Resumo agregado (semanal/mensal/anual) de publicações + engajamento de um setor, pro dashboard. */
    public DashboardResumoDTO obterDashboard(Setor setor, String periodo) {
        LocalDate fim = LocalDate.now();
        LocalDate inicio = switch (periodo == null ? "SEMANAL" : periodo.toUpperCase()) {
            case "MENSAL" -> fim.minusDays(29);
            case "ANUAL" -> fim.minusDays(364);
            default -> fim.minusDays(6);
        };
        String periodoNormalizado = switch (periodo == null ? "SEMANAL" : periodo.toUpperCase()) {
            case "MENSAL" -> "MENSAL";
            case "ANUAL" -> "ANUAL";
            default -> "SEMANAL";
        };

        List<Cartao> publicados = cartaoRepository.findBySetorAndEtapaAndDataPublicacaoBetween(setor, Etapa.PUBLICADO, inicio, fim);

        long totalCurtidas = 0, totalComentarios = 0, totalSalvamentos = 0, totalCompartilhamentos = 0, totalAlcance = 0;
        int totalVinculados = 0;
        List<DashboardPostDTO> posts = new java.util.ArrayList<>();

        for (Cartao cartao : publicados) {
            boolean vinculado = cartao.getInstagramMediaId() != null && !cartao.getInstagramMediaId().isBlank();
            MetricaPostagem ultima = vinculado
                    ? metricaRepository.findFirstByCartaoIdOrderByColetadoEmDesc(cartao.getId()).orElse(null)
                    : null;

            if (vinculado) totalVinculados++;
            if (ultima != null) {
                totalCurtidas += nz(ultima.getCurtidas());
                totalComentarios += nz(ultima.getComentarios());
                totalSalvamentos += nz(ultima.getSalvamentos());
                totalCompartilhamentos += nz(ultima.getCompartilhamentos());
                totalAlcance += nz(ultima.getAlcance());
            }

            posts.add(new DashboardPostDTO(
                    cartao.getId(), cartao.getTitulo(), cartao.getFormato(), cartao.getDataPublicacao(),
                    cartao.getInstagramPermalink(), vinculado,
                    ultima != null ? ultima.getCurtidas() : null,
                    ultima != null ? ultima.getComentarios() : null,
                    ultima != null ? ultima.getSalvamentos() : null,
                    ultima != null ? ultima.getCompartilhamentos() : null,
                    ultima != null ? ultima.getAlcance() : null,
                    ultima != null ? ultima.getColetadoEm() : null
            ));
        }

        posts.sort((a, b) -> {
            if (a.dataPublicacao() == null) return 1;
            if (b.dataPublicacao() == null) return -1;
            return b.dataPublicacao().compareTo(a.dataPublicacao());
        });

        return new DashboardResumoDTO(periodoNormalizado, inicio, fim, publicados.size(), totalVinculados,
                totalCurtidas, totalComentarios, totalSalvamentos, totalCompartilhamentos, totalAlcance, posts);
    }

    private static int nz(Integer valor) {
        return valor != null ? valor : 0;
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
        if (!usuario.temPoderesDeCoordenadorGeral()) {
            throw new IllegalStateException("Apenas o Coordenador Geral pode gerenciar a conexão com o Instagram.");
        }
    }

    private record InsightsResponse(List<InsightItem> data) {}
    private record InsightItem(String name, List<InsightValue> values) {}
    private record InsightValue(Integer value) {}

    private record MediaResponse(List<MediaItem> data) {}
    private record MediaItem(String id, String caption, String media_type, String permalink, String timestamp,
                              Integer like_count, Integer comments_count) {}
}
