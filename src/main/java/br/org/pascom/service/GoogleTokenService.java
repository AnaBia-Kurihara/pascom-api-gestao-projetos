package br.org.pascom.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Verifica o ID token que o front-end recebe do "Entrar com Google" (Google Identity
 * Services). Validação simples via endpoint público do Google, sem precisar de biblioteca
 * cliente — mesmo padrão de chamada HTTP direta já usado para a Graph API do Instagram.
 */
@Service
public class GoogleTokenService {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token={idToken}";

    @Value("${api.security.google.client-id}")
    private String clientId;

    private final RestClient restClient = RestClient.create();

    public GoogleClaims verificar(String idToken) {
        Map<String, Object> resposta;
        try {
            resposta = restClient.get().uri(TOKENINFO_URL, idToken).retrieve().body(Map.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Não foi possível verificar o login do Google.", e);
        }

        if (resposta == null || resposta.get("email") == null) {
            throw new IllegalStateException("Token do Google inválido.");
        }
        if (clientId == null || clientId.isBlank() || !clientId.equals(resposta.get("aud"))) {
            throw new IllegalStateException("Token do Google não pertence a este aplicativo.");
        }
        if (!"true".equals(String.valueOf(resposta.get("email_verified")))) {
            throw new IllegalStateException("O e-mail dessa conta Google ainda não foi verificado.");
        }

        String email = (String) resposta.get("email");
        String nome = (String) resposta.getOrDefault("name", email);
        return new GoogleClaims(email, nome);
    }

    public record GoogleClaims(String email, String nome) {}
}
