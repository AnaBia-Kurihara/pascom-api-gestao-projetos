package br.org.pascom.dto;

import br.org.pascom.model.enums.Role;
import br.org.pascom.model.enums.Setor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioCadastroDTO(
        @NotBlank(message = "O nome é obrigatório") String nome,
        @Email @NotBlank(message = "O e-mail é obrigatório") String email,
        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 6, message = "A senha precisa ter pelo menos 6 caracteres") String senha,
        @NotNull(message = "Selecione se é voluntário, coordenador de setor ou coordenador geral") Role role,
        // Obrigatório salvo quando role = COORDENADOR_GERAL (esse coordena todos os setores).
        Setor setor,
        String funcao,
        String disponibilidade,
        Integer avatarHue
) {}