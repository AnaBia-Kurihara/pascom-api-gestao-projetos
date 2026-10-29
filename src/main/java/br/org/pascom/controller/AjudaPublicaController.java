package br.org.pascom.controller;

import br.org.pascom.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** "Precisa de ajuda?" da tela de entrada (sem login): WhatsApp de quem da coordenação aceitou aparecer. */
@RestController
@RequestMapping("/api/publico/ajuda")
public class AjudaPublicaController {

    private final UsuarioService usuarioService;

    public AjudaPublicaController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> contatos() {
        return ResponseEntity.ok(usuarioService.contatosDeAjuda());
    }
}
