package br.org.pascom.model;

import br.org.pascom.model.enums.MotivoExclusao;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_eventos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O título do evento é obrigatório")
    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private LocalTime horario;

    @Column(nullable = false)
    private String local;

    @Builder.Default
    private Boolean eventoGrande = false; // Ex: Festa de Nossa Senhora de Fátima

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SlotEscala> slots = new ArrayList<>();

    /** Preenchido quando o evento vai pra lixeira; null enquanto estiver ativo. */
    private LocalDateTime excluidoEm;

    @Enumerated(EnumType.STRING)
    private MotivoExclusao motivoExclusao;

    @Column(columnDefinition = "TEXT")
    private String detalheExclusao;
}