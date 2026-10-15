package br.org.pascom.service;

import br.org.pascom.model.Usuario;
import br.org.pascom.repository.UsuarioRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Aniversariantes do dia: às 7h (Brasília) a coordenação recebe no sininho "Hoje é aniversário de
 * Fulano — toque pra gerar o post" (tocar abre o gerador do post de parabéns) e o aniversariante
 * recebe os parabéns. Também roda na subida da aplicação (se já passou das 7h), sem repetir quem já
 * recebeu hoje — cobre o servidor estar fora do ar no horário.
 */
@Service
public class AniversarioService {

    static final LocalTime HORARIO = LocalTime.of(7, 0);

    private final UsuarioRepository usuarioRepository;
    private final AvisoService avisoService;

    public AniversarioService(UsuarioRepository usuarioRepository, AvisoService avisoService) {
        this.usuarioRepository = usuarioRepository;
        this.avisoService = avisoService;
    }

    @Scheduled(cron = "0 0 7 * * *", zone = CalendarioService.FUSO)
    public void avisarAgendado() {
        avisarAniversariantesDeHoje();
    }

    /** Na subida: só se já passou do horário (antes disso, o agendamento das 7h cuida). */
    public int avisarSeJaPassouDoHorario() {
        LocalTime agora = LocalTime.now(ZoneId.of(CalendarioService.FUSO));
        return agora.isBefore(HORARIO) ? 0 : avisarAniversariantesDeHoje();
    }

    /** @return quantos avisos novos foram criados */
    public int avisarAniversariantesDeHoje() {
        LocalDate hoje = LocalDate.now(ZoneId.of(CalendarioService.FUSO));
        LocalDateTime inicioDoDia = hoje.atStartOfDay();
        List<Usuario> todos = usuarioRepository.findAll();
        List<Usuario> aniversariantes = todos.stream().filter(u -> fazAniversario(u, hoje)).toList();
        if (aniversariantes.isEmpty()) return 0;

        List<Usuario> coordenacao = todos.stream().filter(u -> u.isCoordenacao() || u.gereTodasAsEscalas()).toList();
        int criados = 0;
        for (Usuario aniv : aniversariantes) {
            String primeiro = aniv.getNome().split(" ")[0];
            String acao = "postAniversario:" + aniv.getId();
            for (Usuario c : coordenacao) {
                if (c.getId().equals(aniv.getId()) || avisoService.jaAvisou(c, acao, inicioDoDia)) continue;
                avisoService.avisar(c, "🎂 Hoje é aniversário de " + aniv.getNome() + "! Toque aqui pra gerar o post de parabéns.", acao);
                criados++;
            }
            String acaoParabens = "parabens:" + aniv.getId();
            if (!avisoService.jaAvisou(aniv, acaoParabens, inicioDoDia)) {
                avisoService.avisar(aniv, "🎉 Feliz aniversário, " + primeiro + "! Hoje é o seu dia. Que Nossa Senhora de Fátima te cubra de bênçãos. A Pascom inteira celebra você! 💙", acaoParabens);
                criados++;
            }
        }
        return criados;
    }

    /** Quem nasceu em 29/02 é lembrado no 28/02 quando o ano não é bissexto. */
    static boolean fazAniversario(Usuario u, LocalDate dia) {
        Integer d = u.getAniversarioDia(), m = u.getAniversarioMes();
        if (d == null || m == null || m != dia.getMonthValue()) return false;
        if (d == dia.getDayOfMonth()) return true;
        return m == 2 && d == 29 && dia.getDayOfMonth() == 28 && !dia.isLeapYear();
    }
}
