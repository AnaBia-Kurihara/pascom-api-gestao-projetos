package br.org.pascom.service;

import br.org.pascom.model.ContaGmail;
import br.org.pascom.model.Usuario;
import br.org.pascom.repository.ContaGmailRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * E-mails do sistema pela conta Gmail da Pascom, usando a API do Gmail por HTTPS (o Railway bloqueia
 * SMTP). Sem domínio próprio o Resend só entrega pro dono da conta; pelo Gmail chega pra qualquer um
 * (limite do Google: ~500 destinatários por dia numa conta comum).
 * <p>
 * Conexão: a Coordenação Geral toca em "Conectar Gmail" → autoriza no Google com a conta da Pascom
 * (só a permissão gmail.send) → o Google volta em /api/gmail/callback com um código → trocamos por um
 * refresh token, guardado criptografado. Pra cada envio pedimos um acesso de 1h (fica em cache).
 */
@Service
public class GmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailService.class);
    private static final String ESCOPO = "https://www.googleapis.com/auth/gmail.send openid email";
    private static final String AUTORIZAR = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN = "https://oauth2.googleapis.com/token";
    private static final String ENVIAR = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final ContaGmailRepository repositorio;
    private final CriptografiaService criptografia;
    private final ObjectMapper json;
    private final String clientId, clientSecret, baseUrl;
    private final RestClient http = RestClient.create();
    private final SecureRandom random = new SecureRandom();
    /** state do OAuth → quem pediu e até quando vale (10 min). Um servidor só, então memória basta. */
    private final Map<String, Map.Entry<String, Instant>> pedidos = new ConcurrentHashMap<>();
    private volatile String acesso;
    private volatile Instant acessoValeAte = Instant.EPOCH;

    public GmailService(ContaGmailRepository repositorio, CriptografiaService criptografia, ObjectMapper json,
                        @Value("${api.gmail.client-id:}") String clientId,
                        @Value("${api.gmail.client-secret:}") String clientSecret,
                        @Value("${app.base-url}") String baseUrl) {
        this.repositorio = repositorio;
        this.criptografia = criptografia;
        this.json = json;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public boolean configurado() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    /** Conectado e funcionando (o Google não recusou o último pedido de acesso). */
    public boolean conectado() {
        return configurado() && repositorio.findById(ContaGmail.ID_UNICO).map(c -> c.getFalhouEm() == null).orElse(false);
    }

    public Map<String, Object> status(Usuario u) {
        exigirGeral(u);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("configurado", configurado());
        repositorio.findById(ContaGmail.ID_UNICO).ifPresentOrElse(c -> {
            r.put("email", c.getEmail());
            r.put("conectadoEm", c.getConectadoEm() == null ? null : c.getConectadoEm().toString());
            r.put("conectadoPor", c.getConectadoPor());
            r.put("funcionando", c.getFalhouEm() == null);
        }, () -> r.put("email", null));
        r.put("redirectUri", redirectUri());
        return r;
    }

    /** Endereço do Google pra onde o navegador vai pra autorizar. */
    public String urlParaConectar(Usuario u) {
        exigirGeral(u);
        if (!configurado()) {
            throw new IllegalStateException("Falta configurar o segredo do Google (GMAIL_CLIENT_SECRET) no Railway.");
        }
        byte[] b = new byte[24];
        random.nextBytes(b);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        Instant agora = Instant.now();
        pedidos.values().removeIf(e -> e.getValue().isBefore(agora));
        pedidos.put(state, Map.entry(u.getNome(), agora.plusSeconds(600)));
        return AUTORIZAR + "?client_id=" + enc(clientId) + "&redirect_uri=" + enc(redirectUri())
                + "&response_type=code&scope=" + enc(ESCOPO) + "&access_type=offline&prompt=consent"
                + "&include_granted_scopes=false&state=" + enc(state);
    }

    /** Volta do Google: troca o código pelo refresh token e guarda. Devolve o e-mail conectado. */
    public String concluir(String code, String state) {
        Map.Entry<String, Instant> pedido = state == null ? null : pedidos.remove(state);
        if (pedido == null || pedido.getValue().isBefore(Instant.now())) {
            throw new IllegalStateException("O pedido de conexão expirou. Toque em Conectar de novo.");
        }
        if (code == null || code.isBlank()) throw new IllegalStateException("O Google não autorizou.");
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("code", code);
        f.add("client_id", clientId);
        f.add("client_secret", clientSecret);
        f.add("redirect_uri", redirectUri());
        f.add("grant_type", "authorization_code");
        JsonNode r = postarFormulario(f);
        String refresh = r.path("refresh_token").asText("");
        if (refresh.isEmpty()) throw new IllegalStateException("O Google não devolveu a autorização permanente. Tente conectar de novo.");
        if (!r.path("scope").asText("").contains("gmail.send")) {
            throw new IllegalStateException("A permissão de enviar e-mails não foi marcada. Conecte de novo e deixe a caixinha marcada.");
        }
        String email = emailDoIdToken(r.path("id_token").asText(""));
        repositorio.save(ContaGmail.builder().id(ContaGmail.ID_UNICO).email(email)
                .refreshTokenCriptografado(criptografia.encriptar(refresh))
                .conectadoEm(LocalDateTime.now()).conectadoPor(pedido.getKey()).build());
        acesso = r.path("access_token").asText(null);
        acessoValeAte = Instant.now().plusSeconds(Math.max(0, r.path("expires_in").asLong(0) - 60));
        log.info("Gmail conectado pra enviar os e-mails do sistema: {}", email);
        return email;
    }

    public void desconectar(Usuario u) {
        exigirGeral(u);
        repositorio.deleteById(ContaGmail.ID_UNICO);
        acesso = null;
        acessoValeAte = Instant.EPOCH;
    }

    /** Envia um e-mail de texto. Devolve false se não deu (quem chama tenta outro caminho). */
    public boolean enviar(String para, String assunto, String texto) {
        ContaGmail conta = repositorio.findById(ContaGmail.ID_UNICO).orElse(null);
        if (!configurado() || conta == null || conta.getFalhouEm() != null || para == null) return false;
        try {
            String raw = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mensagem(conta.getEmail(), para, assunto, texto).getBytes(StandardCharsets.UTF_8));
            http.post().uri(ENVIAR).header("Authorization", "Bearer " + acesso(conta))
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("raw", raw))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("Gmail não enviou pra {}: {}", para, e.getMessage());
            return false;
        }
    }

    /** Mensagem no formato de e-mail (RFC 5322), com assunto e corpo em UTF-8. */
    static String mensagem(String de, String para, String assunto, String texto) {
        String b64 = Base64.getMimeEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
        return "From: =?UTF-8?B?" + Base64.getEncoder().encodeToString("Pascom Fátima".getBytes(StandardCharsets.UTF_8)) + "?= <" + de + ">\r\n"
                + "To: " + para.replaceAll("[\\r\\n]", "") + "\r\n"
                + "Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(assunto.replaceAll("[\\r\\n]", " ").getBytes(StandardCharsets.UTF_8)) + "?=\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n" + b64 + "\r\n";
    }

    private synchronized String acesso(ContaGmail conta) {
        if (acesso != null && Instant.now().isBefore(acessoValeAte)) return acesso;
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("client_id", clientId);
        f.add("client_secret", clientSecret);
        f.add("refresh_token", criptografia.decriptar(conta.getRefreshTokenCriptografado()));
        f.add("grant_type", "refresh_token");
        try {
            JsonNode r = postarFormulario(f);
            acesso = r.path("access_token").asText();
            acessoValeAte = Instant.now().plusSeconds(Math.max(0, r.path("expires_in").asLong(3600) - 60));
            return acesso;
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("invalid_grant")) {
                // Tiraram o acesso na conta do Google (ou trocaram a senha): para de tentar até reconectar.
                conta.setFalhouEm(LocalDateTime.now());
                repositorio.save(conta);
            }
            throw e;
        }
    }

    private JsonNode postarFormulario(MultiValueMap<String, String> f) {
        try {
            String corpo = http.post().uri(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(f).retrieve().body(String.class);
            return json.readTree(corpo);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("O Google recusou: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new IllegalStateException("Não consegui falar com o Google: " + e.getMessage());
        }
    }

    private String emailDoIdToken(String idToken) {
        try {
            String[] partes = idToken.split("\\.");
            JsonNode corpo = json.readTree(Base64.getUrlDecoder().decode(partes[1]));
            return corpo.path("email").asText("conta do Google");
        } catch (Exception e) {
            return "conta do Google";
        }
    }

    public String redirectUri() {
        return baseUrl + "/api/gmail/callback";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static void exigirGeral(Usuario u) {
        if (u == null || !u.temPoderesDeCoordenadorGeral()) {
            throw new IllegalStateException("Só a Coordenação Geral configura os e-mails do sistema.");
        }
    }
}
