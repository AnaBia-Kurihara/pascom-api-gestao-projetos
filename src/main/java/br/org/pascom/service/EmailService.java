package br.org.pascom.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Envio de e-mails transacionais (hoje: redefinição de senha) via a API HTTPS do Resend
 * (resend.com) — não usamos SMTP porque o Railway bloqueia as portas de saída (25/465/587).
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

    public EmailService() {
        this.restClient = RestClient.create();
    }

    public void enviarLinkRedefinicaoSenha(String destinatario, String token) {
        String link = baseUrl + "/?resetToken=" + token;

        String texto =
                "Alguém (esperamos que você) pediu para redefinir a senha da sua conta na Pascom Fátima.\n\n" +
                "Clique no link abaixo para escolher uma nova senha (válido por 1 hora):\n" + link + "\n\n" +
                "Se você não pediu isso, pode ignorar este e-mail — sua senha continua a mesma.";

        Map<String, Object> corpo = Map.of(
                "from", remetente,
                "to", List.of(destinatario),
                "subject", "Pascom Fátima — Redefinição de senha",
                "text", texto
        );

        try {
            restClient.post()
                    .uri(RESEND_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new IllegalStateException("Não foi possível enviar o e-mail de redefinição agora. Tente de novo em alguns minutos.", e);
        }
    }
}
