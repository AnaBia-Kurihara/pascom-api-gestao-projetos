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
    private final br.org.pascom.service.ParticipacaoService participacaoService;
    private final br.org.pascom.service.SugestaoEscalaService sugestaoService;

    public EscalaController(CalendarioService calendarioService, EventoService eventoService, EscalaPdfService escalaPdfService,
                            br.org.pascom.service.ParticipacaoService participacaoService,
                            br.org.pascom.service.SugestaoEscalaService sugestaoService) {
        this.sugestaoService = sugestaoService;
        this.calendarioService = calendarioService;
        this.eventoService = eventoService;
        this.escalaPdfService = escalaPdfService;
        this.participacaoService = participacaoService;
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

    /**
     * Escala de um período (?inicio=AAAA-MM-DD&fim=AAAA-MM-DD) com os setores escolhidos juntos
     * (?setores=DATASHOW,HOLYRICS; vazio = todos). É o que a exportação em imagem/PDF usa.
     */
    @GetMapping("/periodo")
    public ResponseEntity<EscalaCompletaDTO> periodo(@RequestParam LocalDate inicio, @RequestParam LocalDate fim,
                                                     @RequestParam(required = false) String setores,
                                                     @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(eventoService.escalaPeriodo(inicio, fim, setores(setores), solicitante));
    }

    @GetMapping(value = "/periodo/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> periodoPdf(@RequestParam LocalDate inicio, @RequestParam LocalDate fim,
                                             @RequestParam(required = false) String setores,
                                             @AuthenticationPrincipal Usuario solicitante) {
        List<Setor> escolhidos = setores(setores);
        byte[] pdf = escalaPdfService.gerar(eventoService.escalaPeriodo(inicio, fim, escolhidos, solicitante));
        String nome = "escala-" + (escolhidos.isEmpty() ? "completa" : escolhidos.stream()
                .map(s -> s.name().toLowerCase(Locale.ROOT).replace('_', '-')).collect(java.util.stream.Collectors.joining("-")))
                + "-" + inicio + "-a-" + fim + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .body(pdf);
    }

    private static List<Setor> setores(String lista) {
        if (lista == null || lista.isBlank()) return List.of();
        List<Setor> r = new java.util.ArrayList<>();
        for (String s : lista.split(",")) {
            if (s.isBlank()) continue;
            try {
                r.add(Setor.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Setor desconhecido: " + s.trim());
            }
        }
        return r;
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

    /** Relatório de participação do setor (quem serve mais, menos e há quanto tempo): só coordenação. */
    @GetMapping("/participacao")
    public ResponseEntity<Map<String, Object>> participacao(@RequestParam Setor setor,
                                                            @RequestParam(defaultValue = "3") int meses,
                                                            @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(participacaoService.doSetor(setor, meses, solicitante));
    }

    /** Sugestão de escala do mês pro setor (só coordenação). Não grava nada: é só a proposta. */
    @GetMapping("/sugestao")
    public ResponseEntity<Map<String, Object>> sugestao(@RequestParam String mes, @RequestParam Setor setor,
                                                        @AuthenticationPrincipal Usuario solicitante) {
        return ResponseEntity.ok(sugestaoService.sugerir(calendarioService.parseMes(mes), setor, solicitante));
    }

    /** Corpo: {"itens": [{"eventoId", "slotId", "usuarioId"}, ...]} — o que a coordenação deixou marcado. */
    @PostMapping("/sugestao/aplicar")
    public ResponseEntity<Map<String, Object>> aplicarSugestao(@RequestParam Setor setor, @org.springframework.web.bind.annotation.RequestBody Map<String, Object> corpo,
                                                               @AuthenticationPrincipal Usuario solicitante) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itens = corpo.get("itens") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        return ResponseEntity.ok(sugestaoService.aplicar(setor, itens, solicitante));
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
