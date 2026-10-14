package br.org.pascom.model;

import br.org.pascom.model.enums.Setor;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Marca que a escala de um setor num mês foi publicada pela coordenação. Enquanto não existe
 * essa linha, a escala daquele setor/mês está "em montagem": a coordenação vê e mexe em tudo,
 * mas a equipe não vê quem foi escalado (só pode se candidatar). Funções gerais (sem setor)
 * não passam por isso.
 */
@Entity
@Table(name = "tb_publicacoes_escala",
        uniqueConstraints = @UniqueConstraint(columnNames = {"mes", "setor"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicacaoEscala {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** AAAA-MM */
    @Column(nullable = false, length = 7)
    private String mes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Setor setor;

    private LocalDateTime publicadaEm;

    @ManyToOne
    @JoinColumn(name = "publicada_por_id")
    private Usuario publicadaPor;
}
