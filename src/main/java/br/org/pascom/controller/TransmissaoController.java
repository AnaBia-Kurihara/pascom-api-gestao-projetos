package br.org.pascom.controller;

import br.org.pascom.dto.TransmissaoRequestDTO;
import br.org.pascom.dto.TransmissaoResponseDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.EtapaTransmissao;
import br.org.pascom.service.TransmissaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transmissoes")
public class TransmissaoController {

    private final TransmissaoService transmissaoService;

    public TransmissaoController(TransmissaoService transmissaoService) {
        this.transmissaoService = transmissaoService;
    }

    @GetMapping
    public ResponseEntity<List<TransmissaoResponseDTO>> listarTodas() {
        return ResponseEntity.ok(transmissaoService.listarTodas());
    }

    @PostMapping
    public ResponseEntity<TransmissaoResponseDTO> criar(@Valid @RequestBody TransmissaoRequestDTO dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transmissaoService.criar(dados));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransmissaoResponseDTO> atualizar(@PathVariable Long id, @Valid @RequestBody TransmissaoRequestDTO dados) {
        return ResponseEntity.ok(transmissaoService.atualizar(id, dados));
    }

    @PatchMapping("/{id}/etapa")
    public ResponseEntity<TransmissaoResponseDTO> moverEtapa(@PathVariable Long id, @RequestParam EtapaTransmissao novaEtapa) {
        return ResponseEntity.ok(transmissaoService.moverEtapa(id, novaEtapa));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id, @AuthenticationPrincipal Usuario solicitante) {
        transmissaoService.deletar(id, solicitante);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/gerar-mes")
    public ResponseEntity<Map<String, Integer>> gerarDoMes(@RequestParam(required = false) String mes,
                                                           @AuthenticationPrincipal Usuario solicitante) {
        YearMonth alvo = mes != null ? YearMonth.parse(mes) : YearMonth.now();
        int criados = transmissaoService.gerarTransmissoesDominicaisDoMes(alvo, solicitante);
        return ResponseEntity.ok(Map.of("transmissoesCriadas", criados));
    }
}
