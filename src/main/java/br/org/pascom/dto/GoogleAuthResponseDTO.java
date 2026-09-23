package br.org.pascom.dto;

/**
 * Se a conta ainda não existir e a chamada não trouxer role/setor, precisaCompletarCadastro
 * vem true (com nome/email pré-preenchidos do Google) e login vem nulo — o front então pede
 * papel+setor e chama o mesmo endpoint de novo. Caso contrário, login vem preenchido.
 */
public record GoogleAuthResponseDTO(
        boolean precisaCompletarCadastro,
        String nome,
        String email,
        LoginResponseDTO login
) {}
