package br.org.pascom.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Uma "fotografia" das métricas de um post do Instagram em um momento específico.
 * Guardamos várias por Cartao ao longo do tempo (não sobrescrevemos), pois a API do
 * Instagram só mantém histórico por 90 dias — quem sustenta a projeção de crescimento
 * de longo prazo é este histórico no nosso próprio banco.
 */
@Entity
@Table(name = "tb_metricas_postagem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricaPostagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cartao_id")
    private Cartao cartao;

    @Column(nullable = false)
    private LocalDateTime coletadoEm;

    private Integer curtidas;
    private Integer comentarios;
    private Integer salvamentos;
    private Integer compartilhamentos;
    private Integer alcance;
    private Integer impressoes;
}
