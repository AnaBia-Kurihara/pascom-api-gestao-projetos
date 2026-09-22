package br.org.pascom.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Representa a conexão com a conta Instagram Business do santuário.
 * Existe no máximo uma linha (id fixo = 1L) — é a conta única da paróquia.
 */
@Entity
@Table(name = "tb_conta_instagram")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContaInstagram {

    public static final Long ID_UNICO = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private String instagramUserId;

    @Column(nullable = false)
    private String username;

    /** Access token de longa duração, sempre armazenado criptografado (ver CriptografiaService). Nunca exposto via API. */
    @Column(nullable = false, columnDefinition = "TEXT")
    @JsonIgnore
    private String accessTokenCriptografado;

    @ManyToOne(optional = false)
    @JoinColumn(name = "conectado_por_id")
    private Usuario conectadoPor;

    @Column(nullable = false)
    private LocalDateTime conectadoEm;

    private LocalDateTime tokenExpiraEm;
}
