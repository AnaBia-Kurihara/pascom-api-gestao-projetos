package br.org.pascom.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Aviso dentro do app pra uma pessoa (ex.: "Você foi escalado(a) em Foto no domingo 22/11"). */
@Entity
@Table(name = "tb_avisos", indexes = @Index(name = "idx_avisos_usuario", columnList = "usuario_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aviso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 500)
    private String texto;

    private LocalDateTime criadoEm;

    /** null = ainda não lido */
    private LocalDateTime lidoEm;

    /**
     * O que o app faz ao tocar no aviso (null = nada). Ex.: "postAniversario:12" abre o gerador do
     * post de parabéns da pessoa 12.
     */
    @Column(length = 100)
    private String acao;
}
