package br.org.pascom.controller;

import br.org.pascom.dto.EscalaCompletaDTO;
import br.org.pascom.service.CalendarioService;
import br.org.pascom.service.EscalaPdfService;
import br.org.pascom.service.EventoService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Calendário de domingos + escala completa (todos os setores juntos), em JSON e em PDF.
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
    public ResponseEntity<EscalaCompletaDTO> completa(@RequestParam(required = false) String mes) {
        return ResponseEntity.ok(eventoService.escalaCompleta(calendarioService.parseMes(mes)));
    }

    @GetMapping(value = "/completa/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> completaPdf(@RequestParam(required = false) String mes) {
        YearMonth alvo = calendarioService.parseMes(mes);
        byte[] pdf = escalaPdfService.gerar(eventoService.escalaCompleta(alvo));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("escala-" + alvo + ".pdf").build().toString())
                .body(pdf);
    }
}
