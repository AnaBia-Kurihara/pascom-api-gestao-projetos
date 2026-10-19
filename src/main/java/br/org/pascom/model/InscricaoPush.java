package br.org.pascom.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Um aparelho (celular ou computador) que aceitou receber notificações de uma pessoa. O endereço
 * (endpoint) é do serviço de push do navegador (Google, Apple, Mozilla, Microsoft); p256dh e auth são
 * as chaves que só aquele aparelho tem pra abrir o conteúdo.
 */
@Entity
@Table(name = "tb_inscricoes_push", indexes = @Index(name = "idx_push_usuario", columnList = "usuario_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InscricaoPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Sem chave estrangeira de propósito: apagar uma pessoa não fica preso nos aparelhos dela. */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, unique = true, length = 1000)
    private String endpoint;

    @Column(nullable = false, length = 200)
    private String p256dh;

    @Column(nullable = false, length = 100)
    private String auth;

    private LocalDateTime criadaEm;

    /** Última vez que o serviço de push aceitou uma notificação pra cá. */
    private LocalDateTime ultimoEnvioEm;
}
