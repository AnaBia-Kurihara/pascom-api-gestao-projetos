package br.org.pascom.dto;

import br.org.pascom.model.enums.Setor;
import br.org.pascom.model.enums.SlotTipo;
import jakarta.validation.constraints.NotBlank;

/**
 * @param tipo  opcional — só usado pras funções clássicas de cobertura (Foto, Vídeo, Stories, Transmissão).
 * @param setor setor dono da função. Coordenador de setor só cria funções do próprio setor (o valor
 *              enviado é ignorado e trocado pelo setor dele); Coordenador Geral escolhe livremente,
 *              e null = função geral, aberta a qualquer setor.
 */
public record SlotEscalaRequestDTO(
        @NotBlank(message = "O nome da função é obrigatório") String rotulo,
        SlotTipo tipo,
        Setor setor
) {}
