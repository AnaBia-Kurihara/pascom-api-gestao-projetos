package br.org.pascom.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Uma música de uma das missas do fim de semana, num momento da missa (Entrada, Comunhão...). */
@Entity
@Table(name = "tb_musicas_missa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MusicaMissa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "montagem_id", nullable = false)
    private MontagemFimDeSemana montagem;

    /** SAB_1630, DOM_0800, DOM_1000 ou DOM_1830 (ver HolyricsService.MISSAS) */
    @Column(nullable = false, length = 20)
    private String missa;

    private Integer ordem;

    @Column(nullable = false, length = 60)
    private String momento;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String letra;

    /** Quem mandou (nome digitado pelo músico no link). */
    @Column(length = 120)
    private String enviadoPor;

    private LocalDateTime atualizadoEm;
}
