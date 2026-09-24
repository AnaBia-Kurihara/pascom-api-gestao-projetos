package br.org.pascom.model;

import br.org.pascom.model.enums.EtapaTransmissao;
import br.org.pascom.model.enums.TipoTransmissao;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Uma transmissão ao vivo (missa dominical, missa solene ou benção do Santíssimo). Pra
 * cada uma, a equipe cria um link e uma capa personalizados com o tema litúrgico do dia.
 */
@Entity
@Table(name = "tb_transmissoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transmissao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "A data é obrigatória")
    @Column(nullable = false)
    private LocalDate data;

    private LocalTime horario;

    @Column(nullable = false)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTransmissao tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EtapaTransmissao etapa;

    /** Tema/leitura do dia — usado pra personalizar a capa e o link. */
    private String temaLiturgico;

    private String link;

    /** Onde está a capa pronta (link do Canva/Drive, por exemplo) — sem upload de arquivo por enquanto. */
    private String capaLink;

    @ManyToOne
    @JoinColumn(name = "responsavel_id")
    private Usuario responsavel;
}
