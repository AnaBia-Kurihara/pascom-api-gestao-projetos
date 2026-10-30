package br.org.pascom.service;

import br.org.pascom.model.CandidaturaEscala;
import br.org.pascom.model.Evento;
import br.org.pascom.model.SlotEscala;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Role;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.repository.CandidaturaEscalaRepository;
import br.org.pascom.repository.EventoRepository;
import br.org.pascom.repository.InscricaoPushRepository;
import br.org.pascom.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Lembrete pra quem ainda não se candidatou a nada no mês: <b>36h e 12h antes de as candidaturas
 * fecharem</b> (pedido da coordenação, out/2026). Vai pro sininho e vira notificação no celular de quem
 * ativou; quem não tem nenhum aparelho com notificação recebe também por e-mail. Só voluntários de um
 * setor que tem função aberta pra candidatura naquele mês; quem já se candidatou ou já foi escalado(a)
 * em alguma função do mês não recebe. Cada lembrete sai uma vez só por pessoa ({@link AvisoService#jaAvisou}).
 */
@Service
public class LembreteCandidaturaService {

    private static final Logger log = LoggerFactory.getLogger(LembreteCandidaturaService.class);
    private static final Locale PT = new Locale("pt", "BR");
    static final int HORAS_PRIMEIRO = 36, HORAS_ULTIMO = 12;

    private final EventoRepository eventoRepository;
    private final CandidaturaEscalaRepository candidaturaRepository;
    private final UsuarioRepository usuarioRepository;
    private final InscricaoPushRepository inscricaoRepository;
    private final AvisoService avisoService;
    private final EmailService emailService;

    public LembreteCandidaturaService(EventoRepository eventoRepository, CandidaturaEscalaRepository candidaturaRepository,
                                      UsuarioRepository usuarioRepository, InscricaoPushRepository inscricaoRepository,
                                      AvisoService avisoService, EmailService emailService) {
        this.eventoRepository = eventoRepository;
        this.candidaturaRepository = candidaturaRepository;
        this.usuarioRepository = usuarioRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.avisoService = avisoService;
        this.emailService = emailService;
    }

    @Scheduled(cron = "0 */15 * * * *", zone = CalendarioService.FUSO)
    public void agendado() {
        try {
            enviar(LocalDateTime.now(ZoneId.of(CalendarioService.FUSO)));
        } catch (RuntimeException e) {
            log.warn("Lembrete de candidatura falhou: {}", e.getMessage());
        }
    }

    /** Manda o lembrete que estiver na hora (36h ou 12h antes de fechar). Devolve quantas pessoas avisou. */
    @Transactional
    public int enviar(LocalDateTime agora) {
        LocalDate hoje = agora.toLocalDate();
        int total = 0;
        for (YearMonth mes : List.of(YearMonth.from(hoje), YearMonth.from(hoje).plusMonths(1))) {
            // As candidaturas valem até o fim do dia anterior ao fechamento (CicloEscala.candidaturaAberta).
            LocalDateTime fecha = CicloEscala.fechaCandidaturas(mes).atStartOfDay();
            if (!agora.isBefore(fecha) || agora.isBefore(fecha.minusHours(HORAS_PRIMEIRO))) continue;
            String marco = agora.isBefore(fecha.minusHours(HORAS_ULTIMO)) ? HORAS_PRIMEIRO + "h" : HORAS_ULTIMO + "h";
            total += avisarMes(mes, marco, fecha, hoje);
        }
        return total;
    }

    private int avisarMes(YearMonth mes, String marco, LocalDateTime fecha, LocalDate hoje) {
        List<Evento> eventos = eventoRepository.findByDataBetweenAndExcluidoEmIsNullOrderByDataAscHorarioAsc(
                CicloEscala.inicio(mes), CicloEscala.fim(mes));
        Set<Setor> setoresAbertos = EnumSet.noneOf(Setor.class);
        Set<Long> ocupados = new HashSet<>();
        List<Long> slotsDoMes = new ArrayList<>();
        for (Evento e : eventos) {
            for (SlotEscala s : e.getSlots()) {
                slotsDoMes.add(s.getId());
                if (s.getVoluntario() != null) ocupados.add(s.getVoluntario().getId());
                if (s.getSetor() != null && CicloEscala.candidaturaAberta(e.getData(), hoje)) setoresAbertos.add(s.getSetor());
            }
        }
        if (setoresAbertos.isEmpty()) return 0;
        Set<Long> candidatos = new HashSet<>();
        if (!slotsDoMes.isEmpty()) {
            for (CandidaturaEscala c : candidaturaRepository.findBySlotIdIn(slotsDoMes)) candidatos.add(c.getUsuario().getId());
        }

        String nomeMes = mes.getMonth().getDisplayName(TextStyle.FULL, PT);
        LocalDate ultimoDia = fecha.toLocalDate().minusDays(1);
        String ate = (ultimoDia.equals(hoje) ? "hoje" : ultimoDia.equals(hoje.plusDays(1)) ? "amanhã"
                : ultimoDia.getDayOfWeek().getDisplayName(TextStyle.FULL, PT).replace("-feira", ""))
                + " (" + ultimoDia.format(DateTimeFormatter.ofPattern("dd/MM")) + "), às 23h59";
        int avisados = 0;
        for (Usuario u : usuarioRepository.findAll()) {
            if (u.getRole() != Role.VOLUNTARIO || u.getEmail() == null
                    || u.getEmail().endsWith(UsuarioService.DOMINIO_SEM_EMAIL)) continue;
            if (candidatos.contains(u.getId()) || ocupados.contains(u.getId())) continue;
            Setor setor = setoresAbertos.stream().filter(u::fazParteDoSetor).findFirst().orElse(null);
            if (setor == null) continue;
            // "candidatura:<mês>:<36h|12h>:<setor>": o app abre a escala desse setor quando a pessoa toca no aviso.
            String acao = "candidatura:" + mes + ":" + marco + ":" + setor.name().toLowerCase();
            if (avisoService.jaAvisou(u, acao, fecha.minusDays(5))) continue;

            String texto = (marco.equals(HORAS_ULTIMO + "h") ? "⏰ Últimas horas! " : "⏰ ")
                    + "As candidaturas de " + nomeMes + " vão até " + ate + " e você ainda não se candidatou. "
                    + "Toque aqui e escolha em quais missas pode servir.";
            avisoService.avisar(u, texto, acao);
            if (inscricaoRepository.findByUsuarioIdOrderByCriadaEmAsc(u.getId()).isEmpty()) {
                boolean saiu = emailService.enviar(u.getEmail(), "Pascom: as candidaturas de " + nomeMes + " estão fechando",
                        "Oi, " + u.getNome().split(" ")[0] + "! Paz e bem.\n\n"
                                + "As candidaturas da escala de " + nomeMes + " vão até " + ate
                                + " e você ainda não se candidatou pra nenhuma missa.\n\n"
                                + "Entre no app, vá em Escalas e toque em \"Quero servir\" nas missas em que você pode:\n"
                                + emailService.linkApp() + "\n\n"
                                + "Depois disso a coordenação monta e publica a escala.\n\n"
                                + "Pascom Santuário Nossa Senhora de Fátima");
                if (saiu) try { Thread.sleep(600); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
            avisados++;
        }
        if (avisados > 0) log.info("Lembrete de candidatura ({} antes) de {}: {} pessoa(s).", marco, mes, avisados);
        return avisados;
    }
}
