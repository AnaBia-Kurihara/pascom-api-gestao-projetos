package br.org.pascom.service;

import br.org.pascom.model.Usuario;
import br.org.pascom.repository.InscricaoPushRepository;
import br.org.pascom.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Aviso único de novembro/2026 (pedido da coordenação): quem ainda não ativou as notificações ou não
 * informou o aniversário recebe um aviso no sininho e, se não tem aparelho com notificação, um e-mail.
 * Roda na subida da aplicação em produção, só até {@link #ULTIMO_DIA}, e nunca repete pra mesma pessoa
 * ({@link AvisoService#jaAvisou}). Depois dessa data não faz mais nada e pode ser apagado.
 */
@Service
public class LembretePendenciasService {

    private static final Logger log = LoggerFactory.getLogger(LembretePendenciasService.class);
    static final LocalDate ULTIMO_DIA = LocalDate.of(2026, 11, 5);
    static final String ACAO = "pendencias:2026-11";

    private final UsuarioRepository usuarioRepository;
    private final InscricaoPushRepository inscricaoRepository;
    private final AvisoService avisoService;
    private final EmailService emailService;
    private final Environment ambiente;

    public LembretePendenciasService(UsuarioRepository usuarioRepository, InscricaoPushRepository inscricaoRepository,
                                     AvisoService avisoService, EmailService emailService, Environment ambiente) {
        this.usuarioRepository = usuarioRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.avisoService = avisoService;
        this.emailService = emailService;
        this.ambiente = ambiente;
    }

    /** Na subida: só em produção, pra rodar local não mandar e-mail pra ninguém. */
    public int enviarNaSubida() {
        if (!ambiente.acceptsProfiles(Profiles.of("prod"))) return 0;
        return enviar(LocalDate.now(ZoneId.of(CalendarioService.FUSO)));
    }

    /** @return quantas pessoas foram avisadas */
    public int enviar(LocalDate hoje) {
        if (hoje.isAfter(ULTIMO_DIA)) return 0;
        int avisados = 0;
        for (Usuario u : usuarioRepository.findAll()) {
            if (u.getEmail() == null || u.getEmail().endsWith(UsuarioService.DOMINIO_SEM_EMAIL)) continue;
            boolean semNotificacao = inscricaoRepository.findByUsuarioIdOrderByCriadaEmAsc(u.getId()).isEmpty();
            boolean semAniversario = u.getAniversarioDia() == null || u.getAniversarioMes() == null;
            if (!semNotificacao && !semAniversario) continue;
            if (avisoService.jaAvisou(u, ACAO, ULTIMO_DIA.minusYears(1).atStartOfDay())) continue;

            // O aviso vem antes do e-mail: se o servidor reiniciar no meio, ninguém recebe o e-mail duas vezes.
            avisoService.avisar(u, textoDoAviso(semNotificacao, semAniversario), ACAO);
            if (semNotificacao) {
                boolean saiu = emailService.enviar(u.getEmail(), assunto(semAniversario), textoDoEmail(u, semAniversario));
                if (saiu) try { Thread.sleep(600); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
            avisados++;
        }
        if (avisados > 0) log.info("Aviso de pendências (notificações/aniversário): {} pessoa(s).", avisados);
        return avisados;
    }

    private String textoDoAviso(boolean semNotificacao, boolean semAniversario) {
        String perfil = "Abra o Meu perfil (toque na sua foto ou no seu nome)";
        if (semNotificacao && semAniversario) {
            return "🎂🔔 Faltam duas coisinhas no seu cadastro: a data do seu aniversário e ativar as notificações do app. "
                    + perfil + ", preencha \"Aniversário\" e, em \"App e notificações\", siga o passo a passo. É rapidinho!";
        }
        if (semAniversario) {
            return "🎂 Falta a data do seu aniversário no cadastro. " + perfil + " e preencha \"Aniversário\". É rapidinho!";
        }
        return "🔔 Ative as notificações do app pra não perder suas escalas e os avisos da Pascom. "
                + perfil + " e, em \"App e notificações\", siga o passo a passo. É rapidinho!";
    }

    private String assunto(boolean semAniversario) {
        return semAniversario ? "Pascom: faltam duas coisinhas no seu cadastro" : "Pascom: ative as notificações do app";
    }

    private String textoDoEmail(Usuario u, boolean semAniversario) {
        return "Oi, " + u.getNome().split(" ")[0] + "! Paz e bem.\n\n"
                + "Pra você não perder suas escalas e os avisos da Pascom, pedimos que ative as notificações do app"
                + (semAniversario ? " e informe a data do seu aniversário (só dia e mês)" : "") + ".\n\n"
                + "É rapidinho: entre no app, toque na sua foto ou no seu nome pra abrir o Meu perfil"
                + (semAniversario ? ", preencha \"Aniversário\"" : "")
                + " e, em \"App e notificações\", siga o passo a passo.\n\n"
                + emailService.linkApp() + "\n\nObrigado(a)!\nPascom Santuário Nossa Senhora de Fátima";
    }
}
