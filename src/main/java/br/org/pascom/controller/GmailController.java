package br.org.pascom.controller;

import br.org.pascom.model.Usuario;
import br.org.pascom.service.EmailService;
import br.org.pascom.service.GmailService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Conexão da conta Gmail da Pascom que manda os e-mails do sistema (só Coordenação Geral). */
@RestController
@RequestMapping("/api/gmail")
public class GmailController {

    private final GmailService gmail;
    private final EmailService email;

    public GmailController(GmailService gmail, EmailService email) {
        this.gmail = gmail;
        this.email = email;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@AuthenticationPrincipal Usuario u) {
        return ResponseEntity.ok(gmail.status(u));
    }

    @PostMapping("/conectar")
    public ResponseEntity<Map<String, String>> conectar(@AuthenticationPrincipal Usuario u) {
        return ResponseEntity.ok(Map.of("url", gmail.urlParaConectar(u)));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> desconectar(@AuthenticationPrincipal Usuario u) {
        gmail.desconectar(u);
        return ResponseEntity.ok(gmail.status(u));
    }

    /** Manda um e-mail de teste pra quem está conectado no app. */
    @PostMapping("/teste")
    public ResponseEntity<Map<String, Object>> teste(@AuthenticationPrincipal Usuario u) {
        gmail.status(u); // confere a permissão
        if (!gmail.conectado()) throw new IllegalStateException("Conecte o Gmail da Pascom antes de testar.");
        boolean ok = gmail.enviar(u.getEmail(), "Pascom Fátima — e-mail de teste",
                "Tudo certo! É assim que os e-mails do sistema (nova senha, lembretes da escala) vão chegar.\n\n" + email.linkApp());
        if (!ok) throw new IllegalStateException("O Gmail não aceitou o envio. Desconecte e conecte de novo.");
        return ResponseEntity.ok(Map.of("enviadoPara", u.getEmail()));
    }

    /** Volta do Google depois de autorizar (público: o navegador chega aqui sem o login do app). */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String destino;
        try {
            if (error != null) throw new IllegalStateException("A autorização foi cancelada no Google.");
            String conta = gmail.concluir(code, state);
            destino = "/?gmail=ok&conta=" + URLEncoder.encode(conta, StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            destino = "/?gmail=erro&motivo=" + URLEncoder.encode(e.getMessage().length() > 200 ? e.getMessage().substring(0, 200) : e.getMessage(), StandardCharsets.UTF_8);
        }
        HttpHeaders h = new HttpHeaders();
        h.setLocation(URI.create(destino));
        return new ResponseEntity<>(h, HttpStatus.FOUND);
    }
}
