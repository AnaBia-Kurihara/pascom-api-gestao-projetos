package br.org.pascom.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Montagem do Holyrics de um fim de semana (sábado + domingo): avisos, músicas de cada uma das 4
 * missas (mandadas pelos músicos pelo link sem login) e se o material já foi montado no Holyrics.
 * A liturgia não é guardada aqui: vem da API de liturgia na hora (LiturgiaService).
 */
@Entity
@Table(name = "tb_montagens_holyrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MontagemFimDeSemana {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Sábado do fim de semana (identifica a montagem). */
    @Column(nullable = false, unique = true)
    private LocalDate sabado;

    @Column(columnDefinition = "TEXT")
    private String avisos;

    /** Token secreto do link dos músicos (sem login). */
    @Column(nullable = false, unique = true, length = 48)
    private String tokenMusicos;

    private LocalDateTime montadoEm;

    @ManyToOne
    @JoinColumn(name = "montado_por_id")
    private Usuario montadoPor;

    @OneToMany(mappedBy = "montagem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("missa ASC, ordem ASC")
    @Builder.Default
    private List<MusicaMissa> musicas = new ArrayList<>();
}
