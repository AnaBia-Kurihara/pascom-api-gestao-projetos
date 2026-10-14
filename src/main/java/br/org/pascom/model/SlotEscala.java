package br.org.pascom.model;

import br.org.pascom.model.enums.Setor;
import br.org.pascom.model.enums.SlotTipo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank; // ← faltava esta linha
import lombok.*;

@Entity
@Table(name = "tb_slots_escala")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotEscala {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String rotulo;

    /** Opcional: só as funções "clássicas" de cobertura (Foto, Vídeo...) têm um tipo; o rótulo é o que aparece pra equipe. */
    @Enumerated(EnumType.STRING)
    private SlotTipo tipo;

    /** Setor responsável por essa função. Null = função geral, aberta a qualquer setor. */
    @Enumerated(EnumType.STRING)
    private Setor setor;

    @ManyToOne
    @JoinColumn(name = "voluntario_id")
    private Usuario voluntario;

    @ManyToOne(optional = false)
    @JoinColumn(name = "evento_id")
    private Evento evento;
}