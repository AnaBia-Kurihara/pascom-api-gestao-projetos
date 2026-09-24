package br.org.pascom.controller;

import br.org.pascom.dto.EventoDTO;
import br.org.pascom.dto.EventoRequestDTO;
import br.org.pascom.dto.ExclusaoRequestDTO;
import br.org.pascom.dto.SlotEscalaRequestDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.service.EventoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

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
    public ResponseEntity<EventoDTO> criar(@Valid @RequestBody EventoRequestDTO dto, @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventoService.criar(dto, solicitante));
    }

    @PostMapping("/{eventoId}/slots")
    public ResponseEntity<EventoDTO> adicionarSlot(@PathVariable Long eventoId, @Valid @RequestBody SlotEscalaRequestDTO dto,
                                                     @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventoService.adicionarSlot(eventoId, dto, solicitante));
    }

    @PostMapping("/gerar-escalas-mes")
    public ResponseEntity<Map<String, Integer>> gerarEscalasDoMes(@RequestParam(required = false) String mes,
                                                                    @AuthenticationPrincipal Usuario solicitante) {
        if (!solicitante.temPoderesDeCoordenadorGeral()) {
            throw new IllegalStateException("Apenas o Coordenador Geral pode gerar as escalas do mês.");
        }
        YearMonth alvo = mes != null ? YearMonth.parse(mes) : YearMonth.now();
        int criados = eventoService.gerarEscalasDominicaisDoMes(alvo);
        return ResponseEntity.ok(Map.of("eventosCriados", criados));
    }

    @PostMapping("/{eventoId}/slots/{slotId}/inscrever")
    public ResponseEntity<EventoDTO> inscrever(@PathVariable Long eventoId, @PathVariable Long slotId, @AuthenticationPrincipal Usuario voluntario) {
        return ResponseEntity.ok(eventoService.inscreverVoluntario(eventoId, slotId, voluntario));
    }

    @PostMapping("/{eventoId}/slots/{slotId}/desinscrever")
    public ResponseEntity<EventoDTO> desinscrever(@PathVariable Long eventoId, @PathVariable Long slotId) {
        return ResponseEntity.ok(eventoService.desinscreverVoluntario(eventoId, slotId));
    }

    @GetMapping("/lixeira")
    public ResponseEntity<List<EventoDTO>> listarLixeira() {
        return ResponseEntity.ok(eventoService.listarLixeira());
    }

    @PostMapping("/{id}/lixeira")
    public ResponseEntity<EventoDTO> moverParaLixeira(@PathVariable Long id, @Valid @RequestBody ExclusaoRequestDTO dados,
                                                        @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(eventoService.moverParaLixeira(id, dados.motivo(), dados.detalhe(), solicitante));
    }

    @PostMapping("/{id}/restaurar")
    public ResponseEntity<EventoDTO> restaurar(@PathVariable Long id, @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(eventoService.restaurar(id, solicitante));
    }
}