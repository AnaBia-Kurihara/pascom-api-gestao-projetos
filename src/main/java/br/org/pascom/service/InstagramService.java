package br.org.pascom.service;

import br.org.pascom.dto.DashboardAutoResumoDTO;
import br.org.pascom.dto.InstagramConectarDTO;
import br.org.pascom.dto.InstagramStatusDTO;
import br.org.pascom.dto.MetaInstagramDTO;
import br.org.pascom.dto.MetaInstagramRequestDTO;
import br.org.pascom.dto.PostagemInstagramDTO;
import br.org.pascom.model.ContaInstagram;
import br.org.pascom.model.MetaInstagram;
import br.org.pascom.model.MetricaInstagram;
import br.org.pascom.model.PostagemInstagram;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.TipoMetaInstagram;
import br.org.pascom.repository.ContaInstagramRepository;
import br.org.pascom.repository.MetaInstagramRepository;
import br.org.pascom.repository.MetricaInstagramRepository;
import br.org.pascom.repository.PostagemInstagramRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Integração com a Instagram Graph API, via "Instagram API with Instagram Login"
 * (Business Login for Instagram — não exige Página do Facebook vinculada).
 *
 * A sincronização é automática: {@link #sincronizarPosts()} lê todos os posts reais da
 * conta conectada (com paginação) e guarda cada um como {@link PostagemInstagram}, com
 * histórico de métricas em {@link MetricaInstagram} — ninguém precisa vincular nada à mão.
 */
@Service
public class InstagramService {

    // graph.facebook.com NÃO funciona para tokens gerados pelo fluxo "Instagram Login"
    // (tokens que começam com "IGAA") — eles exigem graph.instagram.com. Confirmado em
    // teste real: "Invalid OAuth access token - Cannot parse access token" com o host errado.
    private static final String GRAPH_BASE_URL = "https://graph.instagram.com/v21.0";
    private static final String METRICAS = "likes,comments,saved,shares,reach";
    private static final int MAX_PAGINAS_SYNC = 10; // até ~500 posts (limit=50/página) numa sincronização

    private final ContaInstagramRepository contaRepository;
    private final PostagemInstagramRepository postagemRepository;
    private final MetricaInstagramRepository metricaRepository;
    private final MetaInstagramRepository metaRepository;
    private final CriptografiaService criptografiaService;
    private final RestClient restClient;

    public InstagramService(ContaInstagramRepository contaRepository, PostagemInstagramRepository postagemRepository,
                             MetricaInstagramRepository metricaRepository, MetaInstagramRepository metaRepository,
                             CriptografiaService criptografiaService) {
        this.contaRepository = contaRepository;
        this.postagemRepository = postagemRepository;
        this.metricaRepository = metricaRepository;
        this.metaRepository = metaRepository;
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

    /**
     * Lê todos os posts reais da conta conectada (seguindo a paginação da Graph API) e
     * detecta automaticamente qualquer um que ainda não conhecíamos, coletando métricas
     * de todos em seguida. Devolve quantos posts novos foram encontrados nessa rodada.
     */
    @Transactional
    public int sincronizarPosts() {
        ContaInstagram conta = contaRepository.findById(ContaInstagram.ID_UNICO).orElse(null);
        if (conta == null) return 0;
        String token = criptografiaService.decriptar(conta.getAccessTokenCriptografado());

        List<MediaItem> todos = new ArrayList<>();
        String url = GRAPH_BASE_URL + "/" + conta.getInstagramUserId()
                + "/media?fields=id,caption,media_type,permalink,timestamp&limit=50&access_token=" + token;
        int paginas = 0;
        while (url != null && paginas < MAX_PAGINAS_SYNC) {
            MediaResponse resposta;
            try {
                resposta = restClient.get().uri(url).retrieve().body(MediaResponse.class);
            } catch (RestClientException e) {
                break; // instabilidade no meio da leitura não pode derrubar o que já foi lido
            }
            if (resposta == null || resposta.data() == null) break;
            todos.addAll(resposta.data());
            url = resposta.paging() != null ? resposta.paging().next() : null;
            paginas++;
        }

        int novos = 0;
        for (MediaItem item : todos) {
            PostagemInstagram postagem = postagemRepository.findByInstagramMediaId(item.id()).orElse(null);
            if (postagem == null) {
                postagem = postagemRepository.save(PostagemInstagram.builder()
                        .instagramMediaId(item.id())
                        .permalink(item.permalink())
                        .legenda(item.caption())
                        .tipoMidia(item.media_type())
                        .publicadoEm(parseTimestamp(item.timestamp()))
                        .detectadoEm(LocalDateTime.now())
                        .build());
                novos++;
            }
            try {
                Map<String, Integer> valores = buscarInsights(postagem.getInstagramMediaId(), token);
                metricaRepository.save(MetricaInstagram.builder()
                        .postagem(postagem)
                        .coletadoEm(LocalDateTime.now())
                        .curtidas(valores.get("likes"))
                        .comentarios(valores.get("comments"))
                        .salvamentos(valores.get("saved"))
                        .compartilhamentos(valores.get("shares"))
                        .alcance(valores.get("reach"))
                        .build());
            } catch (Exception ignorada) {
                // uma falha isolada (ex.: post muito antigo sem insights) não trava os demais
            }
        }
        return novos;
    }

    /** Roda a cada 6h: mantém a leitura da conta sempre em dia, sem precisar de ação manual. */
    @Scheduled(cron = "0 0 */6 * * *")
    public void sincronizarPostsAgendado() {
        try {
            sincronizarPosts();
        } catch (Exception ignorada) {
            // próxima rodada tenta de novo
        }
    }

    /** Resumo agregado (semanal/mensal/anual) de todos os posts detectados automaticamente. */
    public DashboardAutoResumoDTO obterResumo(String periodo) {
        LocalDate fim = LocalDate.now();
        String periodoNormalizado = normalizarPeriodo(periodo);
        LocalDate inicio = switch (periodoNormalizado) {
            case "MENSAL" -> fim.minusDays(29);
            case "ANUAL" -> fim.minusDays(364);
            default -> fim.minusDays(6);
        };

        List<PostagemInstagram> postagens = postagemRepository.findByPublicadoEmBetween(
                inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay());

        long totalCurtidas = 0, totalComentarios = 0, totalSalvamentos = 0, totalCompartilhamentos = 0, totalAlcance = 0;
        List<PostagemInstagramDTO> dtos = new ArrayList<>();

        for (PostagemInstagram p : postagens) {
            MetricaInstagram ultima = metricaRepository.findFirstByPostagemIdOrderByColetadoEmDesc(p.getId()).orElse(null);
            if (ultima != null) {
                totalCurtidas += nz(ultima.getCurtidas());
                totalComentarios += nz(ultima.getComentarios());
                totalSalvamentos += nz(ultima.getSalvamentos());
                totalCompartilhamentos += nz(ultima.getCompartilhamentos());
                totalAlcance += nz(ultima.getAlcance());
            }
            dtos.add(new PostagemInstagramDTO(
                    p.getId(), p.getInstagramMediaId(), p.getPermalink(), p.getLegenda(), p.getTipoMidia(), p.getPublicadoEm(),
                    ultima != null ? ultima.getCurtidas() : null,
                    ultima != null ? ultima.getComentarios() : null,
                    ultima != null ? ultima.getSalvamentos() : null,
                    ultima != null ? ultima.getCompartilhamentos() : null,
                    ultima != null ? ultima.getAlcance() : null,
                    ultima != null ? ultima.getColetadoEm() : null
            ));
        }

        dtos.sort((a, b) -> {
            if (a.publicadoEm() == null) return 1;
            if (b.publicadoEm() == null) return -1;
            return b.publicadoEm().compareTo(a.publicadoEm());
        });

        double media = dtos.isEmpty() ? 0 : (double) totalCurtidas / dtos.size();

        return new DashboardAutoResumoDTO(periodoNormalizado, inicio, fim, dtos.size(),
                totalCurtidas, totalComentarios, totalSalvamentos, totalCompartilhamentos, totalAlcance, media, dtos);
    }

    @Transactional
    public MetaInstagramDTO criarMeta(MetaInstagramRequestDTO dados, Usuario solicitante) {
        exigirCoordenacao(solicitante);
        MetaInstagram meta = MetaInstagram.builder()
                .titulo(dados.titulo())
                .descricao(dados.descricao())
                .metrica(dados.metrica())
                .valorAlvo(dados.valorAlvo())
                .dataAlvo(dados.dataAlvo())
                .criadoPor(solicitante)
                .criadoEm(LocalDateTime.now())
                .concluida(false)
                .build();
        meta = metaRepository.save(meta);
        return MetaInstagramDTO.from(meta, calcularValorAtual(meta.getMetrica()));
    }

    public List<MetaInstagramDTO> listarMetas() {
        return metaRepository.findAllByOrderByCriadoEmDesc().stream()
                .map(m -> MetaInstagramDTO.from(m, calcularValorAtual(m.getMetrica())))
                .toList();
    }

    @Transactional
    public void excluirMeta(Long id, Usuario solicitante) {
        exigirCoordenacao(solicitante);
        if (!metaRepository.existsById(id)) {
            throw new IllegalArgumentException("Meta não encontrada.");
        }
        metaRepository.deleteById(id);
    }

    private double calcularValorAtual(TipoMetaInstagram tipo) {
        LocalDateTime agora = LocalDateTime.now();
        return switch (tipo) {
            case POSTS_POR_SEMANA -> postagemRepository.findByPublicadoEmBetween(agora.minusDays(7), agora).size();
            case ALCANCE_MENSAL -> {
                LocalDateTime inicioMes = agora.toLocalDate().withDayOfMonth(1).atStartOfDay();
                yield somaMetricaDoPeriodo(inicioMes, agora, MetricaInstagram::getAlcance);
            }
            case CURTIDAS_MEDIA -> mediaMetricaUltimos30Dias(agora, MetricaInstagram::getCurtidas);
            case ENGAJAMENTO_MEDIO -> mediaMetricaUltimos30Dias(agora, m ->
                    nz(m.getCurtidas()) + nz(m.getComentarios()) + nz(m.getSalvamentos()) + nz(m.getCompartilhamentos()));
        };
    }

    private long somaMetricaDoPeriodo(LocalDateTime inicio, LocalDateTime fim, java.util.function.Function<MetricaInstagram, Integer> campo) {
        long soma = 0;
        for (PostagemInstagram p : postagemRepository.findByPublicadoEmBetween(inicio, fim)) {
            MetricaInstagram m = metricaRepository.findFirstByPostagemIdOrderByColetadoEmDesc(p.getId()).orElse(null);
            if (m != null) soma += nz(campo.apply(m));
        }
        return soma;
    }

    private double mediaMetricaUltimos30Dias(LocalDateTime agora, java.util.function.Function<MetricaInstagram, Integer> campo) {
        List<PostagemInstagram> posts = postagemRepository.findByPublicadoEmBetween(agora.minusDays(30), agora);
        if (posts.isEmpty()) return 0;
        long soma = 0;
        int comMetrica = 0;
        for (PostagemInstagram p : posts) {
            MetricaInstagram m = metricaRepository.findFirstByPostagemIdOrderByColetadoEmDesc(p.getId()).orElse(null);
            if (m != null) {
                soma += nz(campo.apply(m));
                comMetrica++;
            }
        }
        return comMetrica == 0 ? 0 : (double) soma / comMetrica;
    }

    private String normalizarPeriodo(String periodo) {
        if (periodo == null) return "SEMANAL";
        return switch (periodo.toUpperCase()) {
            case "MENSAL" -> "MENSAL";
            case "ANUAL" -> "ANUAL";
            default -> "SEMANAL";
        };
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null) return null;
        try {
            return OffsetDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toLocalDateTime();
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(timestamp).toLocalDateTime();
            } catch (Exception e2) {
                return null;
            }
        }
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

    private void exigirCoordenacao(Usuario usuario) {
        if (!usuario.temPoderesDeCoordenadorGeral()) {
            throw new IllegalStateException("Apenas o Coordenador Geral pode gerenciar a conta do Instagram e as metas.");
        }
    }

    private record InsightsResponse(List<InsightItem> data) {}
    private record InsightItem(String name, List<InsightValue> values) {}
    private record InsightValue(Integer value) {}

    private record MediaResponse(List<MediaItem> data, Paging paging) {}
    private record MediaItem(String id, String caption, String media_type, String permalink, String timestamp) {}
    private record Paging(String next) {}
}
