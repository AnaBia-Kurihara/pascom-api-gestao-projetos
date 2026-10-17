package br.org.pascom.controller;

import br.org.pascom.service.HolyricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Link dos músicos (sem login): o token secreto do fim de semana é a "senha". Usado pela página
 * pública musicos.html. Só lê/grava as músicas daquele fim de semana — mais nada do sistema.
 */
@RestController
@RequestMapping("/api/publico/musicos")
public class MusicosPublicoController {

    private final HolyricsService holyricsService;

    public MusicosPublicoController(HolyricsService holyricsService) {
        this.holyricsService = holyricsService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Map<String, Object>> ver(@PathVariable String token) {
        return ResponseEntity.ok(holyricsService.paraMusicos(token));
    }

    /** Corpo: {"enviadoPor": "Ministério Fulano", "musicas": [{"momento","titulo","letra"}]} */
    @PutMapping("/{token}/{missa}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> salvar(@PathVariable String token, @PathVariable String missa,
                                                      @RequestBody Map<String, Object> corpo) {
        Object lista = corpo.get("musicas");
        List<Map<String, String>> musicas = lista instanceof List<?> l ? (List<Map<String, String>>) l : List.of();
        return ResponseEntity.ok(holyricsService.salvarDosMusicos(token, missa, (String) corpo.get("enviadoPor"), musicas));
    }
}
