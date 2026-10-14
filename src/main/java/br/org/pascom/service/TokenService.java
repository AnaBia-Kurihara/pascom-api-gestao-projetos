package br.org.pascom.service;

import br.org.pascom.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class TokenService {

    private static final String EMISSOR = "pascom-fatima-api";

    @Value("${api.security.token.secret}")
    private String secret;

    /** Quanto tempo a pessoa fica logada sem precisar entrar de novo (padrão: 7 dias). */
    @Value("${api.security.token.validade-horas:168}")
    private long validadeHoras;

    public String gerarToken(Usuario usuario) {
        return Jwts.builder()
                .issuer(EMISSOR)
                .subject(usuario.getEmail())
                .claim("id", usuario.getId())
                .claim("role", usuario.getRole().name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(validadeHoras, ChronoUnit.HOURS)))
                .signWith(chaveAssinatura())
                .compact();
    }

    /**
     * Valida o token (assinatura, emissor e expiração) e devolve o e-mail (subject).
     * Retorna null se o token for inválido, expirado ou adulterado.
     */
    public String validarTokenERetornarEmail(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(chaveAssinatura())
                    .requireIssuer(EMISSOR)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private SecretKey chaveAssinatura() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
