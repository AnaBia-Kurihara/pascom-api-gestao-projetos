package br.org.pascom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class PascomFatimaApiApplication {

    static {
        // O servidor do Railway roda em UTC: sem isso todo LocalDateTime.now() saía 3h adiantado
        // (avisos "agora" por 3 horas, Assistente achando que já era amanhã depois das 21h).
        // Bloco estático (e não no main) pra valer também nos testes.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    }

    public static void main(String[] args) {
        SpringApplication.run(PascomFatimaApiApplication.class, args);
    }
}
