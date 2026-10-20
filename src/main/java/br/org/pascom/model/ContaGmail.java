package br.org.pascom.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A conta Gmail da Pascom que manda os e-mails do sistema (uma linha só, id 1). Guarda só o
 * refresh token, criptografado: com ele o servidor pede ao Google um acesso de 1h quando precisa.
 * A autorização dá só a permissão de ENVIAR (gmail.send): o sistema não lê a caixa de entrada.
 */
@Entity
@Table(name = "tb_conta_gmail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContaGmail {

    public static final long ID_UNICO = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, length = 1000)
    private String refreshTokenCriptografado;

    private LocalDateTime conectadoEm;

    private String conectadoPor;

    /** Preenchido quando o Google recusou o token (ex.: alguém tirou o acesso na conta do Google). */
    private LocalDateTime falhouEm;
}
