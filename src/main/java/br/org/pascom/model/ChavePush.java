package br.org.pascom.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * As chaves VAPID do servidor (uma linha só, id 1): a pública vai pro navegador na hora de ativar as
 * notificações, a privada assina cada envio e fica criptografada (CriptografiaService). Trocar estas
 * chaves invalida todas as inscrições: cada pessoa teria que ativar de novo.
 */
@Entity
@Table(name = "tb_chave_push")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChavePush {

    public static final long ID_UNICO = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 200)
    private String publica;

    @Column(nullable = false, length = 500)
    private String privadaCriptografada;
}
