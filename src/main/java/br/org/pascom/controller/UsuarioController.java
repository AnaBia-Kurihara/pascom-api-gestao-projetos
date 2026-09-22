package br.org.pascom.controller;

import br.org.pascom.dto.LoginDTO;
import br.org.pascom.dto.UsuarioCadastroDTO;
import br.org.pascom.dto.UsuarioResponseDTO;
import br.org.pascom.model.enums.Role;
import br.org.pascom.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<List<UsuarioResponseDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    @PostMapping("/cadastro")
    public ResponseEntity<UsuarioResponseDTO> cadastrar(@Valid @RequestBody UsuarioCadastroDTO dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.cadastrar(dados));
    }

    @PostMapping("/login")
    public ResponseEntity<UsuarioResponseDTO> login(@Valid @RequestBody LoginDTO dados) {
        return ResponseEntity.ok(usuarioService.login(dados.email(), dados.senha()));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<UsuarioResponseDTO> alterarRole(
            @PathVariable Long id,
            @RequestParam Role novoRole,
            @RequestParam Long solicitanteId) {
        return ResponseEntity.ok(usuarioService.alterarRole(id, novoRole, solicitanteId));
    }
}