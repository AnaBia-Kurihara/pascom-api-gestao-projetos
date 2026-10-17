package br.org.pascom.service;

import br.org.pascom.model.AvisoPastoral;
import br.org.pascom.model.Evento;
import br.org.pascom.model.MontagemFimDeSemana;
import br.org.pascom.model.MusicaMissa;
import br.org.pascom.model.SlotEscala;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.repository.EventoRepository;
import br.org.pascom.repository.MontagemFimDeSemanaRepository;
import br.org.pascom.repository.PublicacaoEscalaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Setor Holyrics: a montagem do material do Datashow das 4 missas do fim de semana. A liturgia é a
 * mesma pro fim de semana inteiro (a missa de sábado à tarde já é a do domingo), então quem está
 * escalado monta uma vez; o que muda entre as missas são as músicas, que os músicos mandam pelo
 * link sem login (token por fim de semana).
 */
@Service
public class HolyricsService {

    /** As 4 missas do fim de semana, na ordem. */
    /** @param titulo título do evento da escala dessa missa (pra achar quem está no Datashow) */
    public record Missa(String chave, String rotulo, int diasDepoisDoSabado, String horario, String titulo) {}

    public static final List<Missa> MISSAS = List.of(
            new Missa("SAB_1630", "Sábado · 16h30", 0, "16:30", "Missa das 16h30"),
            new Missa("DOM_0800", "Domingo · 8h", 1, "08:00", "Missa das 8h"),
            new Missa("DOM_1000", "Domingo · 10h", 1, "10:00", "Missa das 10h"),
            new Missa("DOM_1830", "Domingo · 18h30", 1, "18:30", "Missa das 18h30"));

    /** Momentos sugeridos (o músico pode deixar em branco o que não tiver). */
    public static final List<String> MOMENTOS = List.of(
            "Entrada", "Ato penitencial", "Glória", "Salmo", "Aclamação ao Evangelho", "Ofertório",
            "Santo", "Cordeiro de Deus", "Comunhão", "Ação de graças", "Final");

    private static final int MAX_MUSICAS_POR_MISSA = 25;
    private static final int MAX_LETRA = 6000;

    private final MontagemFimDeSemanaRepository montagemRepository;
    private final EventoRepository eventoRepository;
    private final PublicacaoEscalaRepository publicacaoRepository;
    private final LiturgiaService liturgiaService;
    private final AvisoService avisoService;
    private final CalendarioService calendarioService;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public HolyricsService(MontagemFimDeSemanaRepository montagemRepository, EventoRepository eventoRepository,
                           PublicacaoEscalaRepository publicacaoRepository, LiturgiaService liturgiaService,
                           AvisoService avisoService, CalendarioService calendarioService, EmailService emailService) {
        this.montagemRepository = montagemRepository;
        this.eventoRepository = eventoRepository;
        this.publicacaoRepository = publicacaoRepository;
        this.liturgiaService = liturgiaService;
        this.avisoService = avisoService;
        this.calendarioService = calendarioService;
        this.emailService = emailService;
    }

    // ---------- equipe (logada) ----------

    /** Próximos fins de semana (o atual, se ainda não acabou, + os 5 seguintes) com o resumo de cada um. */
    @Transactional
    public List<Map<String, Object>> proximos(Usuario viewer) {
        exigirAcesso(viewer);
        LocalDate hoje = calendarioService.hoje();
        LocalDate sabado = hoje.getDayOfWeek() == DayOfWeek.SUNDAY ? hoje.minusDays(1)
                : hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        List<Map<String, Object>> lista = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            MontagemFimDeSemana m = obterOuCriar(sabado.plusWeeks(i));
            Map<String, Object> r = resumo(m, viewer);
            lista.add(r);
        }
        return lista;
    }

    /** Tudo de um fim de semana: liturgia do domingo, músicas por missa, avisos e o link dos músicos. */
    @Transactional
    public Map<String, Object> detalhe(LocalDate sabado, Usuario viewer) {
        exigirAcesso(viewer);
        MontagemFimDeSemana m = obterOuCriar(sabadoDe(sabado));
        Map<String, Object> r = resumo(m, viewer);
        r.put("avisos", m.getAvisos());
        r.put("missas", musicasPorMissa(m));
        r.put("momentos", MOMENTOS);
        r.put("linkMusicos", emailService.linkApp() + "/musicos.html?t=" + m.getTokenMusicos());
        r.put("linkAvisos", emailService.linkApp() + "/avisos.html?t=" + m.getTokenAvisos());
        r.put("avisosPastorais", avisosPastoraisDe(m));
        r.put("avisosMudaramDepoisDeMontado", m.getMontadoEm() != null && (
                (m.getAvisosAtualizadosEm() != null && m.getAvisosAtualizadosEm().isAfter(m.getMontadoEm()))
                        || m.getAvisosPastorais().stream().anyMatch(a -> a.getCriadoEm().isAfter(m.getMontadoEm()))));
        try {
            r.put("liturgia", liturgiaService.doDia(m.getSabado().plusDays(1)));
        } catch (IllegalStateException e) {
            r.put("liturgiaErro", e.getMessage());
        }
        return r;
    }

    @Transactional
    public Map<String, Object> salvarAvisos(LocalDate sabado, String avisos, Usuario viewer) {
        exigirAcesso(viewer);
        MontagemFimDeSemana m = obterOuCriar(sabadoDe(sabado));
        m.setAvisos(avisos == null ? null : avisos.length() > 8000 ? avisos.substring(0, 8000) : avisos);
        m.setAvisosAtualizadosEm(LocalDateTime.now());
        montagemRepository.save(m);
        return detalhe(m.getSabado(), viewer);
    }

    @Transactional
    public Map<String, Object> marcarMontado(LocalDate sabado, boolean montado, Usuario viewer) {
        exigirAcesso(viewer);
        MontagemFimDeSemana m = obterOuCriar(sabadoDe(sabado));
        m.setMontadoEm(montado ? LocalDateTime.now() : null);
        m.setMontadoPor(montado ? viewer : null);
        montagemRepository.save(m);
        return detalhe(m.getSabado(), viewer);
    }

    /** A equipe tira um aviso de pastoral (ex.: duplicado ou já passado pros avisos). */
    @Transactional
    public Map<String, Object> removerAvisoPastoral(LocalDate sabado, Long id, Usuario viewer) {
        exigirAcesso(viewer);
        MontagemFimDeSemana m = obterOuCriar(sabadoDe(sabado));
        m.getAvisosPastorais().removeIf(a -> a.getId().equals(id));
        montagemRepository.save(m);
        return detalhe(m.getSabado(), viewer);
    }

    /** Troca o link dos músicos (ex.: o link vazou pra fora do grupo). */
    @Transactional
    public Map<String, Object> novoLink(LocalDate sabado, Usuario viewer) {
        exigirAcesso(viewer);
        MontagemFimDeSemana m = obterOuCriar(sabadoDe(sabado));
        m.setTokenMusicos(novoToken());
        m.setTokenAvisos(novoToken());
        montagemRepository.save(m);
        return detalhe(m.getSabado(), viewer);
    }

    // ---------- músicos (link sem login) ----------

    @Transactional(readOnly = true)
    public Map<String, Object> paraMusicos(String token) {
        MontagemFimDeSemana m = porToken(token);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sabado", m.getSabado().toString());
        r.put("domingo", m.getSabado().plusDays(1).toString());
        r.put("missas", musicasPorMissa(m));
        r.put("momentos", MOMENTOS);
        return r;
    }

    /** O músico manda (ou corrige) a lista de músicas de uma missa: substitui a lista daquela missa. */
    @Transactional
    public Map<String, Object> salvarDosMusicos(String token, String chaveMissa, String enviadoPor, List<Map<String, String>> musicas) {
        MontagemFimDeSemana m = porToken(token);
        Missa missa = MISSAS.stream().filter(x -> x.chave().equals(chaveMissa)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missa inválida."));
        String nomeDigitado = enviadoPor == null ? "" : enviadoPor.trim();
        if (nomeDigitado.isEmpty()) throw new IllegalArgumentException("Diga o seu nome (ou do ministério de música).");
        final String quem = nomeDigitado.length() > 120 ? nomeDigitado.substring(0, 120) : nomeDigitado;
        if (musicas == null) musicas = List.of();
        if (musicas.size() > MAX_MUSICAS_POR_MISSA) throw new IllegalArgumentException("Músicas demais nessa missa.");

        m.getMusicas().removeIf(x -> x.getMissa().equals(missa.chave()));
        int ordem = 0;
        for (Map<String, String> item : musicas) {
            String titulo = limpar(item.get("titulo"), 200);
            if (titulo == null) continue;
            String momento = limpar(item.get("momento"), 60);
            m.getMusicas().add(MusicaMissa.builder()
                    .montagem(m).missa(missa.chave()).ordem(ordem++)
                    .momento(momento == null ? "Outro" : momento)
                    .titulo(titulo)
                    .letra(limpar(item.get("letra"), MAX_LETRA))
                    .enviadoPor(quem)
                    .atualizadoEm(LocalDateTime.now())
                    .build());
        }
        montagemRepository.save(m);
        String quando = missa.rotulo().replace(" · ", " às ") + " (fim de semana de " + diaMes(m.getSabado()) + ")";
        if (m.getMontadoEm() != null) {
            // Mudança de última hora: quem montou, quem está escalado e o Datashow daquela missa precisam saber.
            alertarMudanca(m, List.of(missa), "⚠️ As músicas da missa de " + quando + " mudaram depois da montagem ("
                    + quem + "). Confira no app e ajuste no Holyrics.");
        } else {
            escalado(m.getSabado(), true).ifPresent(u -> avisoService.avisar(u,
                    "🎵 " + quem + " mandou as músicas da missa de " + quando + "."));
        }
        return paraMusicos(token);
    }

    // ---------- outras pastorais (link sem login pros avisos) ----------

    @Transactional(readOnly = true)
    public Map<String, Object> paraPastorais(String token) {
        MontagemFimDeSemana m = porTokenAvisos(token);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sabado", m.getSabado().toString());
        r.put("domingo", m.getSabado().plusDays(1).toString());
        r.put("missas", MISSAS.stream().map(x -> Map.of("chave", x.chave(), "rotulo", x.rotulo())).toList());
        r.put("avisos", avisosPastoraisDe(m));
        return r;
    }

    @Transactional
    public Map<String, Object> adicionarAvisoPastoral(String token, String pastoral, String texto, String chaveMissa) {
        MontagemFimDeSemana m = porTokenAvisos(token);
        String nome = limpar(pastoral, 120), corpo = limpar(texto, 3000);
        if (nome == null) throw new IllegalArgumentException("Diga o nome da pastoral (ou o seu).");
        if (corpo == null) throw new IllegalArgumentException("Escreva o aviso.");
        if (m.getAvisosPastorais().size() >= 40) throw new IllegalStateException("Avisos demais nesse fim de semana.");
        Missa missa = chaveMissa == null || chaveMissa.isBlank() ? null : MISSAS.stream()
                .filter(x -> x.chave().equals(chaveMissa)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missa inválida."));
        m.getAvisosPastorais().add(AvisoPastoral.builder().montagem(m).pastoral(nome).texto(corpo)
                .missa(missa == null ? null : missa.chave()).criadoEm(LocalDateTime.now()).build());
        montagemRepository.save(m);
        String onde = missa == null ? "das missas do fim de semana de " + diaMes(m.getSabado())
                : "da missa de " + missa.rotulo().replace(" · ", " às ") + " (" + diaMes(m.getSabado()) + ")";
        if (m.getMontadoEm() != null) {
            alertarMudanca(m, missa == null ? MISSAS : List.of(missa),
                    "⚠️ Chegou um aviso novo de " + nome + " " + onde + " depois da montagem. Confira no app e inclua no Holyrics.");
        } else {
            escalado(m.getSabado(), true).ifPresent(u -> avisoService.avisar(u, "📢 " + nome + " mandou um aviso " + onde + "."));
        }
        return paraPastorais(token);
    }

    // ---------- lembretes gentis (prazos são referência, não regra) ----------

    /** Quinta, 10h: se faltar música de alguma missa do fim de semana, um lembrete gentil pra quem monta. */
    @Scheduled(cron = "0 0 10 * * THU", zone = CalendarioService.FUSO)
    @Transactional
    public void lembreteDeQuinta() {
        LocalDate sabado = calendarioService.hoje().with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        MontagemFimDeSemana m = obterOuCriar(sabado);
        List<String> faltam = MISSAS.stream()
                .filter(x -> m.getMusicas().stream().noneMatch(mu -> mu.getMissa().equals(x.chave())))
                .map(x -> x.rotulo().replace(" · ", " "))
                .toList();
        if (faltam.isEmpty()) return;
        escalado(sabado, true).ifPresent(u -> avisoService.avisar(u, "🎵 Oi! Pro fim de semana de " + diaMes(sabado)
                + ", ainda não chegaram as músicas de: " + String.join(", ", faltam)
                + ". Sem pressa: se precisar, reenvie o link pros músicos pela tela de Montagem."));
    }

    /** Sexta, 10h: lembrete gentil pra deixar liturgia e avisos prontos (se ainda não marcou como montado). */
    @Scheduled(cron = "0 0 10 * * FRI", zone = CalendarioService.FUSO)
    @Transactional
    public void lembreteDeSexta() {
        LocalDate sabado = calendarioService.hoje().with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        MontagemFimDeSemana m = obterOuCriar(sabado);
        if (m.getMontadoEm() != null) return;
        escalado(sabado, true).ifPresent(u -> avisoService.avisar(u, "📖 Se der, hoje é um bom dia pra deixar a liturgia e os "
                + "avisos do fim de semana de " + diaMes(sabado) + " prontos no Holyrics. Avisos de outras pastorais podem "
                + "chegar mais tarde, e o app te avisa se chegar algo depois de montado."));
    }

    // ---------- apoio ----------

    /**
     * Mudou algo depois de "Montado no Holyrics": avisa quem montou, quem está escalado pra montar e
     * quem está no Datashow das missas afetadas (só quem já aparece em escala publicada).
     */
    private void alertarMudanca(MontagemFimDeSemana m, List<Missa> missas, String texto) {
        Map<Long, Usuario> pessoas = new LinkedHashMap<>();
        if (m.getMontadoPor() != null) pessoas.put(m.getMontadoPor().getId(), m.getMontadoPor());
        escalado(m.getSabado(), true).ifPresent(u -> pessoas.put(u.getId(), u));
        for (Missa x : missas) {
            LocalDate dia = m.getSabado().plusDays(x.diasDepoisDoSabado());
            if (publicacaoRepository.findByMesAndSetor(YearMonth.from(dia).toString(), Setor.DATASHOW).isEmpty()) continue;
            eventoRepository.findFirstByDataAndTituloAndExcluidoEmIsNull(dia, x.titulo()).ifPresent(e ->
                    e.getSlots().stream().filter(s -> s.getSetor() == Setor.DATASHOW && s.getVoluntario() != null)
                            .forEach(s -> pessoas.put(s.getVoluntario().getId(), s.getVoluntario())));
        }
        pessoas.values().forEach(u -> avisoService.avisar(u, texto));
    }

    private List<Map<String, Object>> avisosPastoraisDe(MontagemFimDeSemana m) {
        return m.getAvisosPastorais().stream().map(a -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", a.getId());
            o.put("pastoral", a.getPastoral());
            o.put("texto", a.getTexto());
            o.put("missa", a.getMissa());
            o.put("missaRotulo", a.getMissa() == null ? "Todas as missas"
                    : MISSAS.stream().filter(x -> x.chave().equals(a.getMissa())).map(Missa::rotulo).findFirst().orElse(a.getMissa()));
            o.put("criadoEm", a.getCriadoEm().toString());
            o.put("depoisDeMontado", m.getMontadoEm() != null && a.getCriadoEm().isAfter(m.getMontadoEm()));
            return o;
        }).toList();
    }

    private MontagemFimDeSemana porTokenAvisos(String token) {
        MontagemFimDeSemana m = montagemRepository.findByTokenAvisos(token == null || token.isBlank() ? "-" : token)
                .orElseThrow(() -> new IllegalArgumentException("Link inválido. Peça um link novo à equipe do Holyrics."));
        if (calendarioService.hoje().isAfter(m.getSabado().plusDays(2))) {
            throw new IllegalStateException("Esse link já expirou: o fim de semana passou. Peça o link do próximo à equipe do Holyrics.");
        }
        return m;
    }

    private static String diaMes(LocalDate d) {
        return d.getDayOfMonth() + "/" + d.getMonthValue();
    }

    private MontagemFimDeSemana porToken(String token) {
        MontagemFimDeSemana m = montagemRepository.findByTokenMusicos(token == null ? "" : token)
                .orElseThrow(() -> new IllegalArgumentException("Link inválido. Peça um link novo à equipe do Holyrics."));
        // O link vale até a segunda-feira depois do fim de semana.
        if (calendarioService.hoje().isAfter(m.getSabado().plusDays(2))) {
            throw new IllegalStateException("Esse link já expirou: o fim de semana passou. Peça o link do próximo à equipe do Holyrics.");
        }
        return m;
    }

    private Map<String, Object> resumo(MontagemFimDeSemana m, Usuario viewer) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sabado", m.getSabado().toString());
        r.put("domingo", m.getSabado().plusDays(1).toString());
        Map<String, Integer> recebidas = new LinkedHashMap<>();
        for (Missa x : MISSAS) {
            recebidas.put(x.chave(), (int) m.getMusicas().stream().filter(mu -> mu.getMissa().equals(x.chave())).count());
        }
        r.put("musicasRecebidas", recebidas);
        r.put("temAvisos", m.getAvisos() != null && !m.getAvisos().isBlank());
        r.put("montado", m.getMontadoEm() != null);
        r.put("montadoPor", m.getMontadoPor() != null ? m.getMontadoPor().getNome() : null);
        r.put("escalado", escaladoVisivel(m.getSabado(), viewer).map(Usuario::getNome).orElse(null));
        return r;
    }

    private List<Map<String, Object>> musicasPorMissa(MontagemFimDeSemana m) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Missa x : MISSAS) {
            List<Map<String, Object>> musicas = m.getMusicas().stream()
                    .filter(mu -> mu.getMissa().equals(x.chave()))
                    .sorted(Comparator.comparing(mu -> mu.getOrdem() == null ? 0 : mu.getOrdem()))
                    .map(mu -> {
                        Map<String, Object> o = new LinkedHashMap<>();
                        o.put("momento", mu.getMomento());
                        o.put("titulo", mu.getTitulo());
                        o.put("letra", mu.getLetra());
                        o.put("enviadoPor", mu.getEnviadoPor());
                        o.put("atualizadoEm", mu.getAtualizadoEm() != null ? mu.getAtualizadoEm().toString() : null);
                        return o;
                    }).toList();
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("chave", x.chave());
            o.put("rotulo", x.rotulo());
            o.put("mudouDepoisDeMontado", m.getMontadoEm() != null && m.getMusicas().stream()
                    .anyMatch(mu -> mu.getMissa().equals(x.chave()) && mu.getAtualizadoEm() != null && mu.getAtualizadoEm().isAfter(m.getMontadoEm())));
            o.put("data", m.getSabado().plusDays(x.diasDepoisDoSabado()).toString());
            o.put("horario", x.horario());
            o.put("musicas", musicas);
            lista.add(o);
        }
        return lista;
    }

    /** Quem está escalado pra montar o fim de semana, respeitando o sigilo da escala em montagem. */
    private Optional<Usuario> escaladoVisivel(LocalDate sabado, Usuario viewer) {
        boolean coord = viewer != null && viewer.coordenaEscalaDoSetor(Setor.HOLYRICS);
        return escalado(sabado, !coord);
    }

    private Optional<Usuario> escalado(LocalDate sabado, boolean soSePublicada) {
        if (soSePublicada && publicacaoRepository.findByMesAndSetor(YearMonth.from(sabado).toString(), Setor.HOLYRICS).isEmpty()) {
            return Optional.empty();
        }
        Optional<Evento> ev = eventoRepository.findFirstByDataAndTituloAndExcluidoEmIsNull(sabado, EventoService.TITULO_MONTAGEM_HOLYRICS);
        return ev.flatMap(e -> e.getSlots().stream()
                .filter(s -> s.getSetor() == Setor.HOLYRICS && s.getVoluntario() != null)
                .map(SlotEscala::getVoluntario).findFirst());
    }

    private MontagemFimDeSemana obterOuCriar(LocalDate sabado) {
        MontagemFimDeSemana m = montagemRepository.findBySabado(sabado).orElseGet(() ->
                montagemRepository.save(MontagemFimDeSemana.builder().sabado(sabado).tokenMusicos(novoToken()).tokenAvisos(novoToken()).build()));
        if (m.getTokenAvisos() == null) { m.setTokenAvisos(novoToken()); m = montagemRepository.save(m); }
        return m;
    }

    private static LocalDate sabadoDe(LocalDate data) {
        if (data == null) throw new IllegalArgumentException("Informe o fim de semana.");
        return data.getDayOfWeek() == DayOfWeek.SUNDAY ? data.minusDays(1)
                : data.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    }

    /** Quem faz parte do Holyrics, quem coordena a escala dele ou a Coordenação Geral. */
    private static void exigirAcesso(Usuario u) {
        if (u == null || !(u.fazParteDoSetor(Setor.HOLYRICS) || u.coordenaEscalaDoSetor(Setor.HOLYRICS))) {
            throw new IllegalStateException("A montagem do Holyrics é só pra equipe do Holyrics e a coordenação.");
        }
    }

    private String novoToken() {
        byte[] b = new byte[24];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String limpar(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
