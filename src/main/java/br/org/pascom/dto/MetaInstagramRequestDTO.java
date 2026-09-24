package br.org.pascom.dto;

import br.org.pascom.model.enums.TipoMetaInstagram;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record MetaInstagramRequestDTO(
        @NotBlank(message = "O título é obrigatório") String titulo,
        String descricao,
        @NotNull(message = "Escolha o tipo de métrica") TipoMetaInstagram metrica,
        @NotNull(message = "Defina o valor alvo") @Positive(message = "O valor alvo precisa ser maior que zero") Integer valorAlvo,
        LocalDate dataAlvo
) {}
