package br.org.pascom.controller;

import br.org.pascom.dto.InstagramConectarDTO;
import br.org.pascom.dto.InstagramStatusDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.service.InstagramService;
import jakarta.validation.Valid;
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
}
