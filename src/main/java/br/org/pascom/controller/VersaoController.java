package br.org.pascom.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Versão publicada (horário do build). Público: o front consulta de tempos em tempos e, quando
 * muda, avisa quem está com a página aberta que saiu uma atualização, sem deslogar ninguém.
 */
@RestController
public class VersaoController {

    private final String versao;

    public VersaoController(ObjectProvider<BuildProperties> build) {
        // No Railway, cada deploy tem um id próprio — é a versão mais confiável (o build do Railway
        // grava uma data fixa de 1980 nos arquivos, então o horário do build-info não muda entre deploys).
        String deploy = System.getenv("RAILWAY_DEPLOYMENT_ID");
        BuildProperties b = build.getIfAvailable();
        if (deploy != null && !deploy.isBlank()) {
            this.versao = deploy;
        } else if (b != null && b.getTime() != null && b.getTime().isAfter(Instant.parse("2000-01-01T00:00:00Z"))) {
            this.versao = b.getTime().toString();
        } else {
            // Sem nada disso (ex.: rodando pela IDE), usa o horário em que a aplicação subiu.
            this.versao = Instant.now().toString();
        }
    }

    @GetMapping("/api/versao")
    public ResponseEntity<Map<String, String>> versao() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("versao", versao));
    }
}
