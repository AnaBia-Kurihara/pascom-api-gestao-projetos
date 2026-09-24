package br.org.pascom.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Uma "fotografia" das métricas de uma PostagemInstagram em um momento específico —
 * guardamos várias ao longo do tempo (histórico), pois a Graph API só mantém 90 dias.
 */
@Entity
@Table(name = "tb_metricas_instagram")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricaInstagram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "postagem_id")
    private PostagemInstagram postagem;

    @Column(nullable = false)
    private LocalDateTime coletadoEm;

    private Integer curtidas;
    private Integer comentarios;
    private Integer salvamentos;
    private Integer compartilhamentos;
    private Integer alcance;
}
