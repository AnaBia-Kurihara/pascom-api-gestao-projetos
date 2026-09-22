package br.org.pascom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PascomFatimaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PascomFatimaApiApplication.class, args);
    }
}
