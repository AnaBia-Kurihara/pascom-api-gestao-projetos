package br.org.pascom.model;

import br.org.pascom.model.enums.EtapaDatashow;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * O preparo + execução do datashow de uma missa específica: monta os slides (lista de
 * músicas da pastoral da música + avisos das outras pastorais + leituras) e depois alguém
 * passa no telão durante a celebração. Quem monta costuma ser combinado por semana; quem
 * passa é combinado por missa — por isso os dois responsáveis ficam em campos separados.
 */
@Entity
@Table(name = "tb_missas_datashow")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissaDatashow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "A data da missa é obrigatória")
    @Column(nullable = false)
    private LocalDate data;

    private LocalTime horario;

    @Column(nullable = false)
    private String local;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EtapaDatashow etapa;

    @Column(columnDefinition = "TEXT")
    private String listaMusicas;

    @Column(columnDefinition = "TEXT")
    private String avisosOutrasPastorais;

    /** Quem monta os slides — geralmente a mesma pessoa cobre a semana inteira. */
    @ManyToOne
    @JoinColumn(name = "responsavel_montagem_id")
    private Usuario responsavelMontagem;

    /** Quem opera o datashow durante essa missa específica. */
    @ManyToOne
    @JoinColumn(name = "responsavel_passar_id")
    private Usuario responsavelPassar;
}
