package br.org.pascom.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Aviso mandado por outra pastoral pelo link sem login (ex.: tema do Grupo de Oração na sexta à noite). */
@Entity
@Table(name = "tb_avisos_pastorais")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvisoPastoral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "montagem_id", nullable = false)
    private MontagemFimDeSemana montagem;

    @Column(nullable = false, length = 120)
    private String pastoral;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    /** Chave da missa (SAB_1630...) ou null = vale pras 4 missas. */
    @Column(length = 20)
    private String missa;

    private LocalDateTime criadoEm;
}
