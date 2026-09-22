package br.org.pascom.controller;

import br.org.pascom.dto.EventoDTO;
import br.org.pascom.dto.EventoRequestDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.service.EventoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    @GetMapping
    public ResponseEntity<List<EventoDTO>> listarTodos() {
        return ResponseEntity.ok(eventoService.listarTodos());
    }

    @PostMapping
    public ResponseEntity<EventoDTO> criar(@Valid @RequestBody EventoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventoService.criar(dto));
    }

    @PostMapping("/{eventoId}/slots/{slotId}/inscrever")
    public ResponseEntity<EventoDTO> inscrever(@PathVariable Long eventoId, @PathVariable Long slotId, @AuthenticationPrincipal Usuario voluntario) {
        return ResponseEntity.ok(eventoService.inscreverVoluntario(eventoId, slotId, voluntario));
    }

    @PostMapping("/{eventoId}/slots/{slotId}/desinscrever")
    public ResponseEntity<EventoDTO> desinscrever(@PathVariable Long eventoId, @PathVariable Long slotId) {
        return ResponseEntity.ok(eventoService.desinscreverVoluntario(eventoId, slotId));
    }
}