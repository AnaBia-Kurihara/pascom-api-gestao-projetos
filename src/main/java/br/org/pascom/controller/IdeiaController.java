package br.org.pascom.controller;

import br.org.pascom.dto.CartaoResponseDTO;
import br.org.pascom.dto.ExclusaoRequestDTO;
import br.org.pascom.dto.IdeiaRequestDTO;
import br.org.pascom.dto.IdeiaResponseDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.service.IdeiaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ideias")
public class IdeiaController {

    private final IdeiaService ideiaService;

    public IdeiaController(IdeiaService ideiaService) {
        this.ideiaService = ideiaService;
    }

    @GetMapping
    public ResponseEntity<List<IdeiaResponseDTO>> listarTodas(@RequestParam(required = false) Setor setor) {
        return ResponseEntity.ok(setor != null ? ideiaService.listarPorSetor(setor) : ideiaService.listarTodas());
    }

    @PostMapping
    public ResponseEntity<IdeiaResponseDTO> criar(@Valid @RequestBody IdeiaRequestDTO dto, @AuthenticationPrincipal Usuario autor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ideiaService.criar(dto, autor));
    }

    @PostMapping("/{id}/votar")
    public ResponseEntity<IdeiaResponseDTO> alternarVoto(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(ideiaService.alternarVoto(id, usuario));
    }

    @PostMapping("/{id}/adotar")
    public ResponseEntity<CartaoResponseDTO> transformarEmCartao(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ideiaService.transformarEmCartao(id));
    }

    @GetMapping("/lixeira")
    public ResponseEntity<List<IdeiaResponseDTO>> listarLixeira(@RequestParam Setor setor) {
        return ResponseEntity.ok(ideiaService.listarLixeira(setor));
    }

    @PostMapping("/{id}/lixeira")
    public ResponseEntity<IdeiaResponseDTO> moverParaLixeira(@PathVariable Long id, @Valid @RequestBody ExclusaoRequestDTO dados) {
        return ResponseEntity.ok(ideiaService.moverParaLixeira(id, dados.motivo(), dados.detalhe()));
    }

    @PostMapping("/{id}/restaurar")
    public ResponseEntity<IdeiaResponseDTO> restaurar(@PathVariable Long id) {
        return ResponseEntity.ok(ideiaService.restaurar(id));
    }
}