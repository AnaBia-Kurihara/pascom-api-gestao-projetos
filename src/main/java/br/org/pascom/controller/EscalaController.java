package br.org.pascom.controller;

import br.org.pascom.dto.EscalaCompletaDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.service.CalendarioService;
import br.org.pascom.service.EscalaPdfService;
import br.org.pascom.service.EventoService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calendário de domingos + escala do mês em JSON e em PDF: completa (todos os setores juntos)
 * ou, com ?setor=X, só a escala daquele setor (+ funções gerais).
 * Aberto a qualquer usuário autenticado — qualquer pessoa da equipe pode ver e baixar.
 */
@RestController
@RequestMapping("/api/escalas")
public class EscalaController {

    private final CalendarioService calendarioService;
    private final EventoService eventoService;
    private final EscalaPdfService escalaPdfService;

    public EscalaController(CalendarioService calendarioService, EventoService eventoService, EscalaPdfService escalaPdfService) {
        this.calendarioService = calendarioService;
        this.eventoService = eventoService;
        this.escalaPdfService = escalaPdfService;
    }

    /** Domingos do mês (?mes=AAAA-MM, padrão = mês atual). */
    @GetMapping("/domingos")
    public ResponseEntity<Map<String, Object>> domingos(@RequestParam(required = false) String mes) {
        YearMonth alvo = calendarioService.parseMes(mes);
        List<LocalDate> domingos = calendarioService.domingosDoMes(alvo);
        return ResponseEntity.ok(Map.of("mes", alvo.toString(), "quantidade", domingos.size(), "domingos", domingos));
    }

    @GetMapping("/completa")
    public ResponseEntity<EscalaCompletaDTO> completa(@RequestParam(required = false) String mes,
                                                      @RequestParam(required = false) Setor setor,
                                                      @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(eventoService.escalaCompleta(calendarioService.parseMes(mes), setor, solicitante));
    }

    /** Coordenação publica a escala do setor no mês: a partir daí a equipe vê quem foi escalado. */
    @PostMapping("/publicar")
    public ResponseEntity<Void> publicar(@RequestParam String mes, @RequestParam Setor setor,
                                         @AuthenticationPrincipal Usuario solicitante) {
        eventoService.publicar(calendarioService.parseMes(mes), setor, solicitante);
        return ResponseEntity.ok().build();
    }

    /** Histórico de alterações da escala de um setor: só coordenação do setor ou Geral. */
    @GetMapping("/historico")
    public ResponseEntity<List<Map<String, Object>>> historico(@RequestParam Setor setor,
                                                               @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(eventoService.historico(setor, solicitante));
    }

    @PostMapping("/despublicar")
    public ResponseEntity<Void> despublicar(@RequestParam String mes, @RequestParam Setor setor,
                                            @AuthenticationPrincipal Usuario solicitante) {
        eventoService.despublicar(calendarioService.parseMes(mes), setor, solicitante);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/completa/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> completaPdf(@RequestParam(required = false) String mes,
                                              @RequestParam(required = false) Setor setor,
                                              @AuthenticationPrincipal Usuario solicitante) {
        YearMonth alvo = calendarioService.parseMes(mes);
        byte[] pdf = escalaPdfService.gerar(eventoService.escalaCompleta(alvo, setor, solicitante));
        String nome = "escala-" + (setor == null ? "completa" : setor.name().toLowerCase(Locale.ROOT).replace('_', '-')) + "-" + alvo + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nome).build().toString())
                .body(pdf);
    }
}
