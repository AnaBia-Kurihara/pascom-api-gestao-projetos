package br.org.pascom.dto;

import br.org.pascom.model.enums.Setor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cadastro de um voluntário feito pela coordenação (pra quem não conseguiu se cadastrar sozinho).
 * E-mail é opcional: sem ele a pessoa entra na equipe e na escala, mas não entra no app.
 * Setor: o coordenador de setor sempre cadastra no dele (o enviado é ignorado); a Geral escolhe.
 */
public record CadastroPelaCoordenacaoDTO(
        @NotBlank(message = "Escreva o nome da pessoa") @Size(max = 120) String nome,
        String email,
        Setor setor,
        @Size(max = 120) String funcao,
        Integer aniversarioDia,
        Integer aniversarioMes
) {}
