package br.org.pascom.controller;

import br.org.pascom.dto.CartaoInstagramDTO;
import br.org.pascom.dto.CartaoRequestDTO;
import br.org.pascom.dto.CartaoResponseDTO;
import br.org.pascom.dto.ComentarioResponseDTO;
import br.org.pascom.dto.MetricaPostagemDTO;
import br.org.pascom.dto.NovoComentarioDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Etapa;
import br.org.pascom.service.CartaoService;
import br.org.pascom.service.InstagramService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cartoes")
public class CartaoController {

    private final CartaoService cartaoService;
    private final InstagramService instagramService;

    public CartaoController(CartaoService cartaoService, InstagramService instagramService) {
        this.cartaoService = cartaoService;
        this.instagramService = instagramService;
    }

    @GetMapping
    public ResponseEntity<List<CartaoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(cartaoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CartaoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(cartaoService.buscarDtoPorId(id));
    }

    @PostMapping
    public ResponseEntity<CartaoResponseDTO> criar(@Valid @RequestBody CartaoRequestDTO cartao) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartaoService.criar(cartao));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CartaoResponseDTO> atualizar(@PathVariable Long id, @Valid @RequestBody CartaoRequestDTO cartao) {
        return ResponseEntity.ok(cartaoService.atualizar(id, cartao));
    }

    @PatchMapping("/{id}/etapa")
    public ResponseEntity<CartaoResponseDTO> moverEtapa(
            @PathVariable Long id,
            @RequestParam Etapa novaEtapa) {
        return ResponseEntity.ok(cartaoService.moverEtapa(id, novaEtapa));
    }

    @PostMapping("/{id}/comentarios")
    public ResponseEntity<ComentarioResponseDTO> adicionarComentario(
            @PathVariable Long id,
            @AuthenticationPrincipal Usuario autor,
            @Valid @RequestBody NovoComentarioDTO payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartaoService.adicionarComentario(id, autor, payload));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        cartaoService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/instagram")
    public ResponseEntity<CartaoResponseDTO> vincularInstagram(@PathVariable Long id, @Valid @RequestBody CartaoInstagramDTO dados) {
        return ResponseEntity.ok(cartaoService.vincularInstagram(id, dados));
    }

    @PostMapping("/{id}/metricas/coletar")
    public ResponseEntity<MetricaPostagemDTO> coletarMetricas(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(instagramService.coletarMetricas(id));
    }

    @GetMapping("/{id}/metricas")
    public ResponseEntity<List<MetricaPostagemDTO>> historicoMetricas(@PathVariable Long id) {
        return ResponseEntity.ok(instagramService.historico(id));
    }
}