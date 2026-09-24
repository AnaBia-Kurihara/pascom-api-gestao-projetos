package br.org.pascom.controller;

import br.org.pascom.dto.DashboardAutoResumoDTO;
import br.org.pascom.dto.InstagramConectarDTO;
import br.org.pascom.dto.InstagramStatusDTO;
import br.org.pascom.dto.MetaInstagramDTO;
import br.org.pascom.dto.MetaInstagramRequestDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.service.InstagramService;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/instagram")
public class InstagramController {

    private final InstagramService instagramService;

    public InstagramController(InstagramService instagramService) {
        this.instagramService = instagramService;
    }

    @GetMapping("/status")
    public ResponseEntity<InstagramStatusDTO> status() {
        return ResponseEntity.ok(instagramService.status());
    }

    @PostMapping("/conectar")
    public ResponseEntity<InstagramStatusDTO> conectar(@Valid @RequestBody InstagramConectarDTO dados,
                                                         @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(instagramService.conectar(dados, solicitante));
    }

    @DeleteMapping("/desconectar")
    public ResponseEntity<Void> desconectar(@AuthenticationPrincipal Usuario solicitante) {
        instagramService.desconectar(solicitante);
        return ResponseEntity.noContent().build();
    }

    /** Dispara na hora uma leitura completa dos posts da conta (a mesma que roda sozinha a cada 6h). */
    @PostMapping("/sincronizar")
    public ResponseEntity<Map<String, Integer>> sincronizar() {
        int novos = instagramService.sincronizarPosts();
        return ResponseEntity.ok(Map.of("postsNovos", novos));
    }

    @GetMapping("/resumo")
    public ResponseEntity<DashboardAutoResumoDTO> resumo(@RequestParam(required = false) String periodo) {
        return ResponseEntity.ok(instagramService.obterResumo(periodo));
    }

    @GetMapping("/metas")
    public ResponseEntity<List<MetaInstagramDTO>> listarMetas() {
        return ResponseEntity.ok(instagramService.listarMetas());
    }

    @PostMapping("/metas")
    public ResponseEntity<MetaInstagramDTO> criarMeta(@Valid @RequestBody MetaInstagramRequestDTO dados,
                                                        @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.status(HttpStatus.CREATED).body(instagramService.criarMeta(dados, solicitante));
    }

    @DeleteMapping("/metas/{id}")
    public ResponseEntity<Void> excluirMeta(@PathVariable Long id, @AuthenticationPrincipal Usuario solicitante) {
        instagramService.excluirMeta(id, solicitante);
        return ResponseEntity.noContent().build();
    }
}
