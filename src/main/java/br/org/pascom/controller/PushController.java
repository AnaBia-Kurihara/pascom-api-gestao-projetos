package br.org.pascom.controller;

import br.org.pascom.model.Usuario;
import br.org.pascom.service.PushService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Notificações no celular: o navegador se inscreve aqui depois que a pessoa toca em "Ativar". */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    @GetMapping("/chave")
    public ResponseEntity<Map<String, String>> chave() {
        return ResponseEntity.ok(Map.of("chave", pushService.chavePublica()));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(pushService.status(usuario));
    }

    /** Corpo: o PushSubscription.toJSON() do navegador ({endpoint, keys: {p256dh, auth}}). */
    @PostMapping("/inscricao")
    public ResponseEntity<Map<String, Object>> inscrever(@RequestBody Map<String, Object> corpo,
                                                         @AuthenticationPrincipal Usuario usuario) {
        Object keys = corpo.get("keys");
        Map<?, ?> k = keys instanceof Map<?, ?> m ? m : Map.of();
        return ResponseEntity.ok(pushService.inscrever(usuario, texto(corpo.get("endpoint")), texto(k.get("p256dh")), texto(k.get("auth"))));
    }

    @PostMapping("/desinscrever")
    public ResponseEntity<Map<String, Object>> desinscrever(@RequestBody Map<String, Object> corpo,
                                                            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(pushService.remover(usuario, texto(corpo.get("endpoint"))));
    }

    @PostMapping("/teste")
    public ResponseEntity<Map<String, Object>> teste(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(pushService.teste(usuario));
    }

    private static String texto(Object o) {
        return o instanceof String s ? s : null;
    }
}
