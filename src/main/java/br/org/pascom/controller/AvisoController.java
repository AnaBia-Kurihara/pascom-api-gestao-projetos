package br.org.pascom.controller;

import br.org.pascom.model.Usuario;
import br.org.pascom.service.AvisoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/avisos")
public class AvisoController {

    private final AvisoService avisoService;

    public AvisoController(AvisoService avisoService) {
        this.avisoService = avisoService;
    }

    /** Os avisos de quem está logado (últimos 30) + quantos ainda não foram lidos. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> meus(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(avisoService.meus(usuario));
    }

    @PostMapping("/lidos")
    public ResponseEntity<Void> marcarLidos(@AuthenticationPrincipal Usuario usuario) {
        avisoService.marcarLidos(usuario);
        return ResponseEntity.ok().build();
    }
}
