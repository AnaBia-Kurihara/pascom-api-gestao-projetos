package br.org.pascom.controller;

import br.org.pascom.dto.MissaDatashowRequestDTO;
import br.org.pascom.dto.MissaDatashowResponseDTO;
import br.org.pascom.model.enums.EtapaDatashow;
import br.org.pascom.service.DatashowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datashow/missas")
public class DatashowController {

    private final DatashowService datashowService;

    public DatashowController(DatashowService datashowService) {
        this.datashowService = datashowService;
    }

    @GetMapping
    public ResponseEntity<List<MissaDatashowResponseDTO>> listarTodas() {
        return ResponseEntity.ok(datashowService.listarTodas());
    }

    @PostMapping
    public ResponseEntity<MissaDatashowResponseDTO> criar(@Valid @RequestBody MissaDatashowRequestDTO dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(datashowService.criar(dados));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MissaDatashowResponseDTO> atualizar(@PathVariable Long id, @Valid @RequestBody MissaDatashowRequestDTO dados) {
        return ResponseEntity.ok(datashowService.atualizar(id, dados));
    }

    @PatchMapping("/{id}/etapa")
    public ResponseEntity<MissaDatashowResponseDTO> moverEtapa(@PathVariable Long id, @RequestParam EtapaDatashow novaEtapa) {
        return ResponseEntity.ok(datashowService.moverEtapa(id, novaEtapa));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        datashowService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}
