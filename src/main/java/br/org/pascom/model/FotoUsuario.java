package br.org.pascom.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Foto de perfil de um {@link Usuario}. Fica separada da tabela de usuários de propósito: o
 * usuário é lido do banco a cada request autenticado (SecurityFilter) e não faz sentido trazer a
 * imagem junto. O front já manda a foto reduzida (quadrada, ~400px, JPEG), então cabe folgado.
 */
@Entity
@Table(name = "tb_fotos_usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FotoUsuario {

    /** Mesmo id do usuário dono da foto (uma foto por pessoa). */
    @Id
    private Long usuarioId;

    @Column(nullable = false, length = FotoUsuario.TAMANHO_MAXIMO)
    private byte[] dados;

    @Column(nullable = false)
    private String contentType;

    public static final int TAMANHO_MAXIMO = 1024 * 1024;
}
