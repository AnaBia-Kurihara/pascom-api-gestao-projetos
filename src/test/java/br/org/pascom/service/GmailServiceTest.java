package br.org.pascom.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** O e-mail montado pro Gmail: acentos no assunto e no texto têm que chegar certos. */
class GmailServiceTest {

    @Test
    void mensagemComAcentosEmUtf8() {
        String m = GmailService.mensagem("pascom@gmail.com", "maria@exemplo.com", "Pascom Fátima — Redefinição de senha",
                "Olá! Clique no link pra criar a nova senha: https://exemplo/?resetToken=abc");

        assertThat(m).contains("To: maria@exemplo.com\r\n").contains("Content-Type: text/plain; charset=UTF-8");
        String assunto = m.replaceAll("(?s).*Subject: =\\?UTF-8\\?B\\?([^?]+)\\?=.*", "$1");
        assertThat(new String(Base64.getDecoder().decode(assunto), StandardCharsets.UTF_8)).isEqualTo("Pascom Fátima — Redefinição de senha");
        String corpo = m.substring(m.indexOf("\r\n\r\n") + 4).replaceAll("\\s", "");
        assertThat(new String(Base64.getDecoder().decode(corpo), StandardCharsets.UTF_8)).startsWith("Olá! Clique no link");
    }

    @Test
    void naoDeixaInjetarCabecalhoPeloDestinatarioOuAssunto() {
        String m = GmailService.mensagem("pascom@gmail.com", "maria@exemplo.com\r\nBcc: outro@x.com", "Oi\r\nBcc: outro@x.com", "texto");
        assertThat(m).doesNotContain("\r\nBcc:");
    }
}
