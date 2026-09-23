package br.org.pascom.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envio de e-mails transacionais (hoje: redefinição de senha) via SMTP,
 * configurado em spring.mail.* (ver application.yml e as env vars MAIL_*).
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void enviarLinkRedefinicaoSenha(String destinatario, String token) {
        String link = baseUrl + "/?resetToken=" + token;

        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setTo(destinatario);
        mensagem.setSubject("Pascom Fátima — Redefinição de senha");
        mensagem.setText(
                "Alguém (esperamos que você) pediu para redefinir a senha da sua conta na Pascom Fátima.\n\n" +
                "Clique no link abaixo para escolher uma nova senha (válido por 1 hora):\n" + link + "\n\n" +
                "Se você não pediu isso, pode ignorar este e-mail — sua senha continua a mesma."
        );

        try {
            mailSender.send(mensagem);
        } catch (MailException e) {
            throw new IllegalStateException("Não foi possível enviar o e-mail de redefinição agora. Tente de novo em alguns minutos.", e);
        }
    }
}
