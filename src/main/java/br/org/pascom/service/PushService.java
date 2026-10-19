package br.org.pascom.service;

import br.org.pascom.model.ChavePush;
import br.org.pascom.model.InscricaoPush;
import br.org.pascom.model.Usuario;
import br.org.pascom.repository.ChavePushRepository;
import br.org.pascom.repository.InscricaoPushRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Notificações no celular (Web Push). Todo aviso do sininho ({@link AvisoService#avisar}) também vira
 * notificação em cada aparelho em que a pessoa ativou. Envia numa thread só, depois que a transação
 * grava o aviso, pra nunca deixar a tela esperando o Google/Apple responderem.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    /** Só esses serviços de push recebem chamadas do servidor (senão alguém faria o servidor chamar qualquer endereço). */
    private static final List<String> SERVICOS_DE_PUSH = List.of(
            "fcm.googleapis.com", ".googleapis.com", ".push.services.mozilla.com", "push.services.mozilla.com",
            "web.push.apple.com", ".push.apple.com", ".notify.windows.com");
    private static final int MAX_APARELHOS_POR_PESSOA = 8;
    /** Se o celular estiver desligado, o serviço de push guarda a notificação por até 2 dias. */
    private static final int VALIDADE_SEGUNDOS = 2 * 24 * 3600;

    private final InscricaoPushRepository inscricaoRepository;
    private final ChavePushRepository chaveRepository;
    private final CriptografiaService criptografia;
    private final ObjectMapper json;
    private final String contato;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    /** Uma thread, fila de até 500 envios (publicar a escala do mês gera dezenas de avisos de uma vez). */
    private final ThreadPoolExecutor fila = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(500), r -> { Thread t = new Thread(r, "push"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardPolicy());
    private final Map<String, Map.Entry<String, Instant>> vapidPorServico = new ConcurrentHashMap<>();
    private volatile KeyPair chaves;

    public PushService(InscricaoPushRepository inscricaoRepository, ChavePushRepository chaveRepository,
                       CriptografiaService criptografia, ObjectMapper json,
                       @Value("${app.base-url}") String baseUrl) {
        this.inscricaoRepository = inscricaoRepository;
        this.chaveRepository = chaveRepository;
        this.criptografia = criptografia;
        this.json = json;
        // A Apple recusa a notificação se o contato não for um https:// ou mailto: de verdade.
        this.contato = baseUrl != null && baseUrl.startsWith("https://") ? baseUrl : "https://pascom-fatima.local";
    }

    // ---------------------------------------------------------------- inscrições

    /** Chave pública que o navegador precisa pra se inscrever (applicationServerKey). */
    public String chavePublica() {
        return WebPushCripto.b64(WebPushCripto.publicaBruta((ECPublicKey) chaves().getPublic()));
    }

    public Map<String, Object> inscrever(Usuario u, String endpoint, String p256dh, String auth) {
        if (endpoint == null || p256dh == null || auth == null) throw new IllegalArgumentException("Inscrição incompleta.");
        URI uri;
        try {
            uri = URI.create(endpoint.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Endereço de notificação inválido.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        boolean conhecido = SERVICOS_DE_PUSH.stream().anyMatch(s -> s.startsWith(".") ? host.endsWith(s) : host.equals(s));
        if (!"https".equals(uri.getScheme()) || !conhecido || endpoint.length() > 1000) {
            throw new IllegalArgumentException("Esse navegador usa um serviço de notificação que o sistema não conhece.");
        }
        byte[] chave = WebPushCripto.deB64(p256dh), segredo = WebPushCripto.deB64(auth);
        WebPushCripto.publicaDeBytes(chave); // confere se é uma chave válida
        if (segredo.length != 16) throw new IllegalArgumentException("Inscrição inválida.");

        // O mesmo aparelho pode trocar de pessoa (ex.: alguém entra com outra conta no celular da família).
        InscricaoPush i = inscricaoRepository.findByEndpoint(endpoint).orElseGet(InscricaoPush::new);
        i.setUsuarioId(u.getId());
        i.setEndpoint(endpoint);
        i.setP256dh(WebPushCripto.b64(chave));
        i.setAuth(WebPushCripto.b64(segredo));
        if (i.getCriadaEm() == null) i.setCriadaEm(LocalDateTime.now());
        inscricaoRepository.save(i);

        List<InscricaoPush> todas = inscricaoRepository.findByUsuarioIdOrderByCriadaEmAsc(u.getId());
        for (int k = 0; k < todas.size() - MAX_APARELHOS_POR_PESSOA; k++) inscricaoRepository.delete(todas.get(k));
        return status(u);
    }

    public Map<String, Object> remover(Usuario u, String endpoint) {
        if (endpoint != null) inscricaoRepository.deleteByEndpointAndUsuarioId(endpoint, u.getId());
        return status(u);
    }

    public Map<String, Object> status(Usuario u) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("aparelhos", inscricaoRepository.findByUsuarioIdOrderByCriadaEmAsc(u.getId()).size());
        return r;
    }

    /** "Enviar teste" do perfil: manda agora pra todos os aparelhos da pessoa. */
    public Map<String, Object> teste(Usuario u) {
        if (inscricaoRepository.findByUsuarioIdOrderByCriadaEmAsc(u.getId()).isEmpty()) {
            throw new IllegalStateException("Nenhum aparelho com notificação ativada ainda.");
        }
        notificar(u.getId(), "Tudo certo! 🙌 É assim que os lembretes da Pascom vão chegar.", null);
        return status(u);
    }

    // ---------------------------------------------------------------- envio

    /** Agenda a notificação pra pessoa (depois do commit, se estiver dentro de uma transação). */
    public void notificar(Long usuarioId, String texto, String acao) {
        if (usuarioId == null || texto == null) return;
        Runnable envio = () -> fila.execute(() -> enviarAgora(usuarioId, texto, acao));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { envio.run(); }
            });
        } else {
            envio.run();
        }
    }

    private void enviarAgora(Long usuarioId, String texto, String acao) {
        try {
            List<InscricaoPush> aparelhos = inscricaoRepository.findByUsuarioIdOrderByCriadaEmAsc(usuarioId);
            if (aparelhos.isEmpty()) return;
            Map<String, Object> dados = new LinkedHashMap<>();
            dados.put("titulo", "Pascom Fátima");
            dados.put("texto", texto);
            dados.put("url", "/?abrir=avisos");
            if (acao != null) dados.put("acao", acao);
            byte[] conteudo = json.writeValueAsBytes(dados);
            for (InscricaoPush a : aparelhos) enviar(a, conteudo);
        } catch (Exception e) {
            log.warn("Falha ao mandar notificação pro usuário {}: {}", usuarioId, e.getMessage());
        }
    }

    private void enviar(InscricaoPush a, byte[] conteudo) {
        try {
            URI uri = URI.create(a.getEndpoint());
            byte[] corpo = WebPushCripto.criptografar(conteudo, WebPushCripto.deB64(a.getP256dh()), WebPushCripto.deB64(a.getAuth()));
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("TTL", String.valueOf(VALIDADE_SEGUNDOS))
                    .header("Urgency", "normal")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", vapid(uri.getScheme() + "://" + uri.getAuthority()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(corpo))
                    .build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int s = r.statusCode();
            if (s == 404 || s == 410) {
                // A pessoa desativou, desinstalou ou o navegador trocou o endereço: não adianta insistir.
                inscricaoRepository.deleteByEndpoint(a.getEndpoint());
            } else if (s >= 200 && s < 300) {
                a.setUltimoEnvioEm(LocalDateTime.now());
                inscricaoRepository.save(a);
            } else {
                log.warn("Serviço de push {} respondeu {}: {}", uri.getHost(), s, r.body().length() > 200 ? r.body().substring(0, 200) : r.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Não consegui mandar notificação pra {}: {}", a.getEndpoint().replaceAll("^(https://[^/]+).*", "$1"), e.getMessage());
        }
    }

    /** A assinatura vale 12h; reaproveita por 6h pra cada serviço de push. */
    private String vapid(String audiencia) {
        Map.Entry<String, Instant> c = vapidPorServico.get(audiencia);
        if (c != null && c.getValue().isAfter(Instant.now())) return c.getKey();
        long exp = Instant.now().plus(Duration.ofHours(12)).getEpochSecond();
        String cab = WebPushCripto.cabecalhoVapid(audiencia, contato, exp, chaves());
        vapidPorServico.put(audiencia, Map.entry(cab, Instant.now().plus(Duration.ofHours(6))));
        return cab;
    }

    private KeyPair chaves() {
        KeyPair k = chaves;
        if (k != null) return k;
        synchronized (this) {
            if (chaves != null) return chaves;
            ChavePush c = chaveRepository.findById(ChavePush.ID_UNICO).orElse(null);
            if (c == null) {
                KeyPair novo = WebPushCripto.novoParDeChaves();
                c = chaveRepository.save(ChavePush.builder().id(ChavePush.ID_UNICO)
                        .publica(WebPushCripto.b64(WebPushCripto.publicaBruta((ECPublicKey) novo.getPublic())))
                        .privadaCriptografada(criptografia.encriptar(WebPushCripto.b64(WebPushCripto.privadaBruta((ECPrivateKey) novo.getPrivate()))))
                        .build());
                log.info("Chaves das notificações no celular criadas.");
            }
            chaves = WebPushCripto.parDeBytes(WebPushCripto.deB64(criptografia.decriptar(c.getPrivadaCriptografada())),
                    WebPushCripto.deB64(c.getPublica()));
            return chaves;
        }
    }
}
