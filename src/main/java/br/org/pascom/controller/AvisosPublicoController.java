package br.org.pascom.controller;

import br.org.pascom.service.HolyricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Link das outras pastorais (sem login) pra mandar avisos das missas do fim de semana, usado pela
 * página pública avisos.html. O token secreto do fim de semana é a "senha".
 */
@RestController
@RequestMapping("/api/publico/avisos")
public class AvisosPublicoController {

    private final HolyricsService holyricsService;

    public AvisosPublicoController(HolyricsService holyricsService) {
        this.holyricsService = holyricsService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> ver(@PathVariable String token) {
        return ResponseEntity.ok(holyricsService.paraPastorais(token));
    }

    /** Corpo: {"pastoral": "Grupo de Oração", "texto": "...", "missa": null | "DOM_1000"} */
    @PostMapping("/{token}")
    public ResponseEntity<Map<String, Object>> enviar(@PathVariable String token, @RequestBody Map<String, String> corpo) {
        return ResponseEntity.ok(holyricsService.adicionarAvisoPastoral(token, corpo.get("pastoral"), corpo.get("texto"), corpo.get("missa")));
    }
}
