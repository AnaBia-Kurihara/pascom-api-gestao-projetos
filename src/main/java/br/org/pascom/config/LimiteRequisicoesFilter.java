package br.org.pascom.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freio pras rotas abertas a qualquer um na internet (login, cadastro, esqueci a senha, links sem
 * login do Holyrics): limita quantas chamadas cada endereço de internet faz por janela de tempo, e
 * recusa corpo de requisição grande demais (com o Java limitado a 200 MB no Railway, um envio enorme
 * derrubava o app). Os limites são folgados de propósito: no dia do lançamento, 40 pessoas se
 * cadastraram, muitas pelo mesmo Wi-Fi da igreja (mesmo endereço).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LimiteRequisicoesFilter extends OncePerRequestFilter {

    /** Maior que a foto de perfil (até 1 MB) com folga. */
    static final long MAX_CORPO_BYTES = 2L * 1024 * 1024;

    private record Regra(String nome, int maximo, long janelaMs) {}

    private static final Regra LOGIN = new Regra("login", 30, 5 * 60_000L);
    private static final Regra CADASTRO = new Regra("cadastro", 60, 10 * 60_000L);
    private static final Regra SENHA = new Regra("senha", 10, 60 * 60_000L);
    private static final Regra PUBLICO_ENVIO = new Regra("publico-envio", 40, 10 * 60_000L);
    private static final Regra PUBLICO_LEITURA = new Regra("publico-leitura", 120, 60_000L);

    private static final class Janela {
        long inicio;
        int contagem;
    }

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private volatile long ultimaLimpeza = System.currentTimeMillis();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getContentLengthLong() > MAX_CORPO_BYTES) {
            responder(res, 413, "Envio grande demais.");
            return;
        }
        Regra regra = regraPara(req.getMethod(), req.getRequestURI());
        if (regra != null && !permitir(regra, ip(req) + "|" + regra.nome())) {
            responder(res, 429, "Muitas tentativas seguidas. Espere alguns minutos e tente de novo.");
            return;
        }
        chain.doFilter(req, res);
    }

    static Regra regraPara(String metodo, String uri) {
        if (!uri.startsWith("/api/")) return null;
        boolean post = "POST".equals(metodo) || "PUT".equals(metodo);
        if (post && (uri.equals("/api/usuarios/login") || uri.equals("/api/usuarios/google"))) return LOGIN;
        if (post && uri.equals("/api/usuarios/cadastro")) return CADASTRO;
        if (post && (uri.equals("/api/usuarios/esqueci-senha") || uri.equals("/api/usuarios/redefinir-senha"))) return SENHA;
        if (uri.startsWith("/api/publico/")) return post ? PUBLICO_ENVIO : PUBLICO_LEITURA;
        return null;
    }

    private boolean permitir(Regra regra, String chave) {
        long agora = System.currentTimeMillis();
        if (agora - ultimaLimpeza > 10 * 60_000L) {
            ultimaLimpeza = agora;
            janelas.values().removeIf(j -> agora - j.inicio > 60 * 60_000L);
        }
        Janela j = janelas.computeIfAbsent(chave, k -> new Janela());
        synchronized (j) {
            if (agora - j.inicio > regra.janelaMs()) {
                j.inicio = agora;
                j.contagem = 0;
            }
            return ++j.contagem <= regra.maximo();
        }
    }

    /**
     * O Railway fica na frente do app e acrescenta no fim do X-Forwarded-For o endereço que viu. O
     * último é o confiável: os primeiros quem chama pode inventar pra fugir do limite.
     */
    private static String ip(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] partes = xff.split(",");
            return partes[partes.length - 1].trim();
        }
        return req.getRemoteAddr();
    }

    private static void responder(HttpServletResponse res, int status, String mensagem) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"erro\":\"" + mensagem + "\"}");
    }
}
