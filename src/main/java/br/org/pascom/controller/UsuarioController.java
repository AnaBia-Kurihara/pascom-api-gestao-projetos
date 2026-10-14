package br.org.pascom.controller;

import br.org.pascom.dto.EsqueciSenhaDTO;
import br.org.pascom.dto.GoogleAuthDTO;
import br.org.pascom.dto.GoogleAuthResponseDTO;
import br.org.pascom.dto.LoginDTO;
import br.org.pascom.dto.LoginResponseDTO;
import br.org.pascom.dto.PerfilUpdateDTO;
import br.org.pascom.dto.RedefinirSenhaDTO;
import br.org.pascom.dto.UsuarioCadastroDTO;
import br.org.pascom.dto.UsuarioResponseDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Role;
import br.org.pascom.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
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
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginDTO dados) {
        return ResponseEntity.ok(usuarioService.login(dados.email(), dados.senha()));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<UsuarioResponseDTO> alterarRole(
            @PathVariable Long id,
            @RequestParam Role novoRole,
            @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(usuarioService.alterarRole(id, novoRole, solicitante));
    }

    /** A própria pessoa edita o perfil (nome, função, disponibilidade, setores extras). */
    @PutMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> atualizarPerfil(@Valid @RequestBody PerfilUpdateDTO dados,
                                                              @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(usuarioService.atualizarPerfil(dados, solicitante));
    }

    /** Corpo = bytes da imagem (JPEG, PNG ou WebP); o tipo é conferido pelos bytes no service. */
    @PutMapping(value = "/me/foto", consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp"})
    public ResponseEntity<UsuarioResponseDTO> salvarFoto(@RequestBody byte[] dados,
                                                         @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(usuarioService.salvarFoto(dados, solicitante));
    }

    @DeleteMapping("/me/foto")
    public ResponseEntity<UsuarioResponseDTO> removerFoto(@AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(usuarioService.removerFoto(solicitante));
    }

    /**
     * Foto de qualquer pessoa da equipe (só pra quem está logado). O front sempre pede com
     * ?v=<fotoVersao>, então dá pra deixar o navegador guardar em cache por bastante tempo.
     */
    @GetMapping("/{id}/foto")
    public ResponseEntity<byte[]> foto(@PathVariable Long id) {
        return usuarioService.buscarFoto(id)
                .map(f -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(f.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                        .body(f.getDados()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/esqueci-senha")
    public ResponseEntity<Void> esqueciSenha(@Valid @RequestBody EsqueciSenhaDTO dados) {
        usuarioService.esqueciSenha(dados.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaDTO dados) {
        usuarioService.redefinirSenha(dados.token(), dados.novaSenha());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/google")
    public ResponseEntity<GoogleAuthResponseDTO> google(@Valid @RequestBody GoogleAuthDTO dados) {
        return ResponseEntity.ok(usuarioService.autenticarComGoogle(dados));
    }
}