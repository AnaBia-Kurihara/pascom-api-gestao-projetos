package br.org.pascom.model;

import br.org.pascom.model.enums.Etapa;
import br.org.pascom.model.enums.Formato;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_cartoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cartao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O título é obrigatório")
    @Column(nullable = false)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Formato formato;

    @Column(nullable = false)
    private String tema;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Etapa etapa;

    @ManyToOne
    @JoinColumn(name = "responsavel_id")
    private Usuario responsavel;

    private LocalDate prazoEntrega;
    private LocalDate dataPublicacao;

    @Column(columnDefinition = "TEXT")
    private String roteiroNotas;

    /** ID da mídia no Instagram (preenchido manualmente após publicar), usado para coletar métricas. */
    private String instagramMediaId;
    private String instagramPermalink;

    @Embedded
    private Checklist checklist;

    @OneToMany(mappedBy = "cartao", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comentario> comentarios = new ArrayList<>();
}