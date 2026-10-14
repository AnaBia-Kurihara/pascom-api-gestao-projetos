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
        BuildProperties b = build.getIfAvailable();
        // Sem build-info (ex.: rodando pela IDE), usa o horário em que a aplicação subiu.
        this.versao = b != null && b.getTime() != null ? b.getTime().toString() : Instant.now().toString();
    }

    @GetMapping("/api/versao")
    public ResponseEntity<Map<String, String>> versao() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("versao", versao));
    }
}
