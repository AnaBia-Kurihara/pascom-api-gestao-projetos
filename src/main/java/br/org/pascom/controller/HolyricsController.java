package br.org.pascom.controller;

import br.org.pascom.model.Usuario;
import br.org.pascom.service.HolyricsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Montagem do Holyrics (equipe do Holyrics + coordenação). */
@RestController
@RequestMapping("/api/holyrics")
public class HolyricsController {

    private final HolyricsService holyricsService;

    public HolyricsController(HolyricsService holyricsService) {
        this.holyricsService = holyricsService;
    }

    @GetMapping("/fins-de-semana")
    public ResponseEntity<List<Map<String, Object>>> proximos(@AuthenticationPrincipal Usuario u) {
        return ResponseEntity.ok(holyricsService.proximos(u));
    }

    /** ?sabado=AAAA-MM-DD (qualquer dia do fim de semana serve) */
    @GetMapping("/montagem")
    public ResponseEntity<Map<String, Object>> detalhe(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sabado,
                                                       @AuthenticationPrincipal Usuario u) {
        return ResponseEntity.ok(holyricsService.detalhe(sabado, u));
    }

    @PutMapping("/montagem/avisos")
    public ResponseEntity<Map<String, Object>> avisos(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sabado,
                                                      @RequestBody Map<String, String> corpo, @AuthenticationPrincipal Usuario u) {
        return ResponseEntity.ok(holyricsService.salvarAvisos(sabado, corpo.get("avisos"), u));
    }

    @PutMapping("/montagem/montado")
    public ResponseEntity<Map<String, Object>> montado(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sabado,
                                                       @RequestBody Map<String, Boolean> corpo, @AuthenticationPrincipal Usuario u) {
        return ResponseEntity.ok(holyricsService.marcarMontado(sabado, Boolean.TRUE.equals(corpo.get("montado")), u));
    }

    @PostMapping("/montagem/novo-link")
    public ResponseEntity<Map<String, Object>> novoLink(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sabado,
                                                        @AuthenticationPrincipal Usuario u) {
        return ResponseEntity.ok(holyricsService.novoLink(sabado, u));
    }
}
