package br.org.pascom.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tb_ideias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ideia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O título é obrigatório")
    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false)
    private String tema;

    @ManyToOne(optional = false)
    @JoinColumn(name = "autor_id")
    private Usuario autor;

    @Builder.Default
    private Boolean adotada = false;

    // Conjunto de IDs dos usuários que votaram nesta ideia
    @ElementCollection
    @CollectionTable(name = "tb_ideia_votos", joinColumns = @JoinColumn(name = "ideia_id"))
    @Column(name = "usuario_id")
    @Builder.Default
    private Set<Long> votantesIds = new HashSet<>();
}
