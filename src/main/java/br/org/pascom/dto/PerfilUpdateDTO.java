package br.org.pascom.dto;

import br.org.pascom.model.enums.Setor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** O que a própria pessoa pode mudar no perfil dela. Papel e setor principal não entram aqui. */
public record PerfilUpdateDTO(
        @NotBlank(message = "O nome é obrigatório") @Size(max = 120, message = "Nome muito longo") String nome,
        @Size(max = 120, message = "Função muito longa") String funcao,
        @Size(max = 200, message = "Disponibilidade muito longa") String disponibilidade,
        /** Outros setores dos quais a pessoa também faz parte (o principal é ignorado se vier aqui). */
        Set<Setor> setoresExtras,
        /** Aniversário (só dia e mês); null nos dois = não muda. */
        Integer aniversarioDia,
        Integer aniversarioMes
) {}
