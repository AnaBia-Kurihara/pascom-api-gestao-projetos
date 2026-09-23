package br.org.pascom.controller;

import br.org.pascom.dto.AgenteMensagemDTO;
import br.org.pascom.dto.AgenteRespostaDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.service.AgenteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agente")
public class AgenteController {

    private final AgenteService agenteService;

    public AgenteController(AgenteService agenteService) {
        this.agenteService = agenteService;
    }

    @PostMapping("/mensagem")
    public ResponseEntity<AgenteRespostaDTO> mensagem(@Valid @RequestBody AgenteMensagemDTO dados,
                                                        @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(agenteService.conversar(dados, solicitante));
    }
}
