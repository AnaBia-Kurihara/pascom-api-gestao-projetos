package br.org.pascom.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Envio de e-mails do sistema (nova senha, lembrete de 48h, pedido de aniversário). Primeiro pela
 * conta Gmail da Pascom ({@link GmailService}), se estiver conectada; senão pela API HTTPS do Resend
 * (resend.com), que sem domínio verificado só entrega pro dono da conta. Não usamos SMTP porque o
 * Railway bloqueia as portas de saída (25/465/587).
 */
@Service
public class EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";

    @Value("${api.resend.chave}")
    private String apiKey;

    @Value("${api.resend.remetente}")
    private String remetente;

    @Value("${app.base-url}")
    private String baseUrl;

    private final RestClient restClient;
    private final GmailService gmail;

    public EmailService(GmailService gmail) {
        this.restClient = RestClient.create();
        this.gmail = gmail;
    }

    public String linkApp() {
        return baseUrl;
    }

    /**
     * Envia um e-mail simples. Não lança exceção: devolve false se falhar (sem chave, Resend
     * recusou etc.), pra quem chamou seguir em frente — ex.: o lembrete de escala também vira
     * aviso no app. Sem domínio verificado em RESEND_FROM, o Resend só entrega pro dono da conta.
     */
    public boolean enviar(String destinatario, String assunto, String texto) {
        if (destinatario != null && gmail.enviar(destinatario, assunto, texto)) return true;
        if (apiKey == null || apiKey.isBlank() || destinatario == null) return false;
        try {
            restClient.post()
                    .uri(RESEND_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("from", remetente, "to", List.of(destinatario), "subject", assunto, "text", texto))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            org.slf4j.LoggerFactory.getLogger(EmailService.class)
                    .warn("E-mail não enviado pra {}: {}", destinatario, e.getMessage());
            return false;
        }
    }

    public String linkRedefinicao(String token) {
        return baseUrl + "/?resetToken=" + token;
    }

    /** Devolve false se o e-mail não saiu (sem domínio verificado no Resend, só chega pro dono da conta). */
    public boolean enviarLinkRedefinicaoSenha(String destinatario, String token) {
        String texto =
                "Alguém (esperamos que você) pediu para redefinir a senha da sua conta na Pascom Fátima.\n\n" +
                "Clique no link abaixo para escolher uma nova senha (válido por 1 hora):\n" + linkRedefinicao(token) + "\n\n" +
                "Se você não pediu isso, pode ignorar este e-mail — sua senha continua a mesma.";
        return enviar(destinatario, "Pascom Fátima — Redefinição de senha", texto);
    }
}
