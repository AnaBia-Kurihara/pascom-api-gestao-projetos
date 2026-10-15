package br.org.pascom.model;

import br.org.pascom.model.enums.Setor;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Histórico de alterações da escala, visível só pra coordenação do setor (e a Geral): quem
 * escalou, trocou, tirou, publicou etc. Guarda o texto pronto (e não o vínculo com a função),
 * pra continuar fazendo sentido mesmo se a função ou o evento forem removidos depois.
 */
@Entity
@Table(name = "tb_historico_escala", indexes = @Index(name = "idx_historico_setor_mes", columnList = "setor,mes"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlteracaoEscala {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Setor setor;

    /** AAAA-MM do evento alterado */
    @Column(nullable = false, length = 7)
    private String mes;

    @Column(nullable = false, length = 500)
    private String texto;

    /** Quem fez; null = o próprio sistema (ex.: publicação automática do dia 1º). */
    @ManyToOne
    @JoinColumn(name = "autor_id")
    private Usuario autor;

    private LocalDateTime criadoEm;
}
