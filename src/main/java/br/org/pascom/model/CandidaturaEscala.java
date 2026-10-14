package br.org.pascom.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * "Quero servir": a pessoa se candidata a uma função da escala. Quem decide quem fica é a
 * coordenação do setor (EventoService.escalar), e o resultado só aparece pra equipe depois
 * que a escala do mês é publicada (ver {@link PublicacaoEscala}).
 */
@Entity
@Table(name = "tb_candidaturas_escala",
        uniqueConstraints = @UniqueConstraint(columnNames = {"slot_id", "usuario_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidaturaEscala {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private SlotEscala slot;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    private LocalDateTime criadaEm;
}
