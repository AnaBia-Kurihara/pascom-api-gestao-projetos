package br.org.pascom.dto;

import br.org.pascom.model.enums.Setor;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AgenteMensagemDTO(
        @NotBlank(message = "Escreva uma mensagem") String mensagem,
        List<AgenteTurnoDTO> historico,
        Setor setor
) {}
