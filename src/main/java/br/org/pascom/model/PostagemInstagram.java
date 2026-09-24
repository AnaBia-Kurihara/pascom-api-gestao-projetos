package br.org.pascom.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Um post real da conta do Instagram, detectado automaticamente pela sincronização
 * periódica (InstagramService.sincronizarPosts) — ninguém precisa vincular manualmente.
 */
@Entity
@Table(name = "tb_postagens_instagram")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostagemInstagram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String instagramMediaId;

    private String permalink;

    @Column(columnDefinition = "TEXT")
    private String legenda;

    private String tipoMidia;

    private LocalDateTime publicadoEm;

    @Column(nullable = false)
    private LocalDateTime detectadoEm;
}
