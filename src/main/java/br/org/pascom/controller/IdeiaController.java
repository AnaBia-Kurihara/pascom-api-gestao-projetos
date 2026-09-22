package br.org.pascom.controller;

import br.org.pascom.dto.CartaoResponseDTO;
import br.org.pascom.dto.IdeiaRequestDTO;
import br.org.pascom.dto.IdeiaResponseDTO;
import br.org.pascom.service.IdeiaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<IdeiaResponseDTO>> listarTodas() {
        return ResponseEntity.ok(ideiaService.listarTodas());
    }

    @PostMapping
    public ResponseEntity<IdeiaResponseDTO> criar(@Valid @RequestBody IdeiaRequestDTO dto, @RequestParam Long autorId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ideiaService.criar(dto, autorId));
    }

    @PostMapping("/{id}/votar")
    public ResponseEntity<IdeiaResponseDTO> alternarVoto(@PathVariable Long id, @RequestParam Long usuarioId) {
        return ResponseEntity.ok(ideiaService.alternarVoto(id, usuarioId));
    }

    @PostMapping("/{id}/adotar")
    public ResponseEntity<CartaoResponseDTO> transformarEmCartao(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ideiaService.transformarEmCartao(id));
    }
}