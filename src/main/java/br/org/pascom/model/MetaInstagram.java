package br.org.pascom.model;

import br.org.pascom.model.enums.TipoMetaInstagram;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Uma meta de crescimento do Instagram, definida pela equipe (ex.: 200 curtidas médias até dezembro). */
@Entity
@Table(name = "tb_metas_instagram")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetaInstagram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O título é obrigatório")
    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMetaInstagram metrica;

    @Column(nullable = false)
    private Integer valorAlvo;

    private LocalDate dataAlvo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    @Builder.Default
    private Boolean concluida = false;
}
