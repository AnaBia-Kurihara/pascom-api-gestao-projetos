package br.org.pascom.dto;

import br.org.pascom.model.enums.Role;
import br.org.pascom.model.enums.Setor;
import jakarta.validation.constraints.NotBlank;

/**
 * role/setor/funcao só são enviados na segunda chamada, quando a conta ainda não existe e
 * a pessoa está completando o cadastro (ver UsuarioService.autenticarComGoogle).
 */
public record GoogleAuthDTO(
        @NotBlank(message = "Token do Google é obrigatório") String idToken,
        Role role,
        Setor setor,
        String funcao
) {}
