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
    public ResponseEntity<List<EventoDTO>> listarTodos(@AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(eventoService.listarTodos(solicitante));
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
        if (!solicitante.gereTodasAsEscalas()) {
            throw new IllegalStateException("Apenas a Coordenação Geral ou quem gere as escalas pode gerar as escalas do mês.");
        }
        YearMonth alvo = mes != null ? YearMonth.parse(mes) : YearMonth.now(java.time.ZoneId.of(br.org.pascom.service.CalendarioService.FUSO));
        int criados = eventoService.gerarEscalasDoMes(alvo);
        return ResponseEntity.ok(Map.of("eventosCriados", criados));
    }

    /** Corpo: {"rotulo": "Câmera 4"}. Coordenação do setor da função ou Coordenador Geral. */
    @PatchMapping("/{eventoId}/slots/{slotId}")
    public ResponseEntity<EventoDTO> renomearSlot(@PathVariable Long eventoId, @PathVariable Long slotId,
                                                  @RequestBody Map<String, String> corpo,
                                                  @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(eventoService.renomearSlot(eventoId, slotId, corpo.get("rotulo"), solicitante));
    }

    @DeleteMapping("/{eventoId}/slots/{slotId}")
    public ResponseEntity<EventoDTO> removerSlot(@PathVariable Long eventoId, @PathVariable Long slotId,
                                                 @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(eventoService.removerSlot(eventoId, slotId, solicitante));
    }

    @PostMapping("/{eventoId}/slots/{slotId}/inscrever")
    public ResponseEntity<EventoDTO> inscrever(@PathVariable Long eventoId, @PathVariable Long slotId, @AuthenticationPrincipal Usuario voluntario) {
        return ResponseEntity.ok(eventoService.inscreverVoluntario(eventoId, slotId, voluntario));
    }

    /** "Quero servir": candidatura a uma função do setor (a coordenação escala e publica). */
    @PostMapping("/{eventoId}/slots/{slotId}/candidatura")
    public ResponseEntity<EventoDTO> candidatar(@PathVariable Long eventoId, @PathVariable Long slotId,
                                                @AuthenticationPrincipal Usuario voluntario) {
        return ResponseEntity.ok(eventoService.candidatar(eventoId, slotId, voluntario));
    }

    @DeleteMapping("/{eventoId}/slots/{slotId}/candidatura")
    public ResponseEntity<EventoDTO> desistir(@PathVariable Long eventoId, @PathVariable Long slotId,
                                              @AuthenticationPrincipal Usuario voluntario) {
        return ResponseEntity.ok(eventoService.desistirCandidatura(eventoId, slotId, voluntario));
    }

    /** Corpo: {"usuarioId": 12}. Coordenação do setor da função põe ou troca a pessoa. */
    @PostMapping("/{eventoId}/slots/{slotId}/escalar")
    public ResponseEntity<EventoDTO> escalar(@PathVariable Long eventoId, @PathVariable Long slotId,
                                             @RequestBody Map<String, Long> corpo,
                                             @AuthenticationPrincipal Usuario solicitante) {
        Long usuarioId = corpo.get("usuarioId");
        if (usuarioId == null) throw new IllegalArgumentException("Escolha quem vai servir.");
        return ResponseEntity.ok(eventoService.escalar(eventoId, slotId, usuarioId, solicitante));
    }

    @PostMapping("/{eventoId}/slots/{slotId}/desinscrever")
    public ResponseEntity<EventoDTO> desinscrever(@PathVariable Long eventoId, @PathVariable Long slotId,
                                                  @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(eventoService.desinscreverVoluntario(eventoId, slotId, solicitante));
    }

    @GetMapping("/lixeira")
    public ResponseEntity<List<EventoDTO>> listarLixeira(@AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(eventoService.listarLixeira(solicitante));
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