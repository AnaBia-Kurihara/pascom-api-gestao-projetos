package br.org.pascom.config;

import br.org.pascom.model.SlotEscala;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.repository.SlotEscalaRepository;
import br.org.pascom.repository.UsuarioRepository;
import br.org.pascom.service.EventoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ajustes idempotentes rodados toda vez que a aplicação sobe (local e produção):
 * <ol>
 *   <li>e-mails já gravados passam pro padrão minúsculo/sem espaços (ver {@link Usuario#normalizarEmail});</li>
 *   <li>liga a flag {@code admin} de quem estiver na env var {@code ADMIN_EMAILS} (separados por vírgula) —
 *       substitui o e-mail que antes ficava fixo no código. Só liga, nunca desliga: pra tirar o admin
 *       de alguém, remova da env var e desligue a coluna {@code admin_sistema} no banco;</li>
 *   <li>funções de escala antigas (de antes da escala por setor) ganham o setor certo;</li>
 *   <li>garante as missas automáticas (domingos e sábados) do mês atual e do próximo, caso o servidor estivesse fora do ar no dia 1.</li>
 * </ol>
 */
@Component
public class InicializacaoDados {

    private static final Logger log = LoggerFactory.getLogger(InicializacaoDados.class);

    private final UsuarioRepository usuarioRepository;
    private final SlotEscalaRepository slotEscalaRepository;
    private final EventoService eventoService;
    private final String adminEmails;

    public InicializacaoDados(UsuarioRepository usuarioRepository, SlotEscalaRepository slotEscalaRepository,
                              EventoService eventoService, @Value("${app.admin-emails:}") String adminEmails) {
        this.usuarioRepository = usuarioRepository;
        this.slotEscalaRepository = slotEscalaRepository;
        this.eventoService = eventoService;
        this.adminEmails = adminEmails;
    }

    // Sem @Transactional de propósito: cada etapa roda na sua própria transação, pra uma falha
    // numa delas não marcar tudo como rollback (e derrubar a inicialização da aplicação).
    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        executarComSeguranca("normalizar e-mails", this::normalizarEmails);
        executarComSeguranca("aplicar ADMIN_EMAILS", this::aplicarAdmins);
        executarComSeguranca("definir setor das funções antigas", this::definirSetorDasFuncoesAntigas);
        executarComSeguranca("ajustar a missa de sábado pras 16h30", () -> {
            int n = eventoService.ajustarMissaDeSabado();
            if (n > 0) log.info("Missas de sábado passadas das 16h pras 16h30: {}", n);
        });
        executarComSeguranca("gerar escalas automáticas", () -> {
            int criados = eventoService.garantirEscalasDoMesAtualEProximo();
            if (criados > 0) log.info("Escalas automáticas (domingos e sábados) criadas na inicialização: {}", criados);
        });
        executarComSeguranca("publicar a escala do mês que chegou", () -> {
            int n = eventoService.publicarMesQueChegou();
            if (n > 0) log.info("Escala do mês atual publicada automaticamente em {} setor(es).", n);
        });
    }

    private void normalizarEmails() {
        List<Usuario> todos = usuarioRepository.findAll();
        Set<String> emUso = todos.stream().map(Usuario::getEmail).collect(Collectors.toSet());
        for (Usuario u : todos) {
            String normalizado = Usuario.normalizarEmail(u.getEmail());
            if (normalizado == null || normalizado.equals(u.getEmail())) continue;
            if (emUso.contains(normalizado)) {
                // Duas contas que só diferem por maiúsculas — não dá pra unificar sozinho sem perder dados.
                log.warn("Não normalizei o e-mail do usuário {}: já existe outra conta com '{}'. Resolva manualmente.", u.getId(), normalizado);
                continue;
            }
            emUso.remove(u.getEmail());
            emUso.add(normalizado);
            u.setEmail(normalizado);
            usuarioRepository.save(u);
        }
    }

    private void aplicarAdmins() {
        if (adminEmails == null || adminEmails.isBlank()) return;
        Arrays.stream(adminEmails.split(","))
                .map(Usuario::normalizarEmail)
                .filter(e -> e != null && !e.isEmpty())
                .forEach(email -> usuarioRepository.findByEmail(email).ifPresent(u -> {
                    if (!u.isAdmin()) {
                        u.setAdmin(true);
                        usuarioRepository.save(u);
                        log.info("Flag admin ligada para o usuário {}", u.getId());
                    }
                }));
    }

    private void definirSetorDasFuncoesAntigas() {
        for (SlotEscala s : slotEscalaRepository.findBySetorIsNullAndTipoIsNotNull()) {
            s.setSetor(switch (s.getTipo()) {
                case TRANSMISSAO -> Setor.TRANSMISSOES_VIDEOS;
                case FOTO, VIDEO, STORIES_AO_VIVO -> Setor.REDES_SOCIAIS;
            });
            slotEscalaRepository.save(s);
        }
    }

    private void executarComSeguranca(String etapa, Runnable acao) {
        try {
            acao.run();
        } catch (Exception e) {
            // Nenhum desses ajustes pode impedir a aplicação de subir.
            log.error("Falha ao {} na inicialização", etapa, e);
        }
    }
}
