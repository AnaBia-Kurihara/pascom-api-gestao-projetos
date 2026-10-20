package br.org.pascom.service;

import br.org.pascom.model.CandidaturaEscala;
import br.org.pascom.model.Evento;
import br.org.pascom.model.SlotEscala;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.repository.CandidaturaEscalaRepository;
import br.org.pascom.repository.EventoRepository;
import br.org.pascom.repository.SlotEscalaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * "Sugerir escala": preenche as vagas abertas de um setor no mês com quem tocou em "Quero servir"
 * naquela função, em rodízio. Regras, nesta ordem:
 * <ol>
 *   <li>só quem se candidatou à função e faz parte do setor (ninguém é escalado sem ter dito que pode);</li>
 *   <li>uma função por missa (contando as funções de todos os setores daquela missa);</li>
 *   <li>quem tem menos escalas no mês primeiro; depois quem não está escalado no mesmo dia; depois
 *       quem menos serviu nos últimos 3 meses; depois quem está há mais tempo sem servir.</li>
 * </ol>
 * As funções com menos candidatos são resolvidas primeiro (senão a única pessoa possível podia ser
 * usada antes numa função que tinha outras opções). Nada é gravado aqui: a coordenação vê a sugestão,
 * desmarca o que não quiser e aplica ({@link #aplicar}), que escala uma a uma pelas regras normais.
 */
@Service
public class SugestaoEscalaService {

    private final EventoRepository eventoRepository;
    private final SlotEscalaRepository slotRepository;
    private final CandidaturaEscalaRepository candidaturaRepository;
    private final EventoService eventoService;
    private final CalendarioService calendarioService;

    public SugestaoEscalaService(EventoRepository eventoRepository, SlotEscalaRepository slotRepository,
                                 CandidaturaEscalaRepository candidaturaRepository, EventoService eventoService,
                                 CalendarioService calendarioService) {
        this.eventoRepository = eventoRepository;
        this.slotRepository = slotRepository;
        this.candidaturaRepository = candidaturaRepository;
        this.eventoService = eventoService;
        this.calendarioService = calendarioService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> sugerir(YearMonth mes, Setor setor, Usuario solicitante) {
        exigirCoordenacao(setor, solicitante);
        LocalDate hoje = calendarioService.hoje();
        LocalDate inicio = mes.atDay(1).isBefore(hoje) ? hoje : mes.atDay(1), fim = mes.atEndOfMonth();
        List<Evento> eventos = inicio.isAfter(fim) ? List.of()
                : eventoRepository.findByDataBetweenAndExcluidoEmIsNullOrderByDataAscHorarioAsc(inicio, fim);

        // Quem já está em cada missa (todas as funções, de todos os setores) e quantas escalas cada um tem no mês.
        Map<Long, Set<Long>> naMissa = new HashMap<>();
        Map<LocalDate, Set<Long>> noDia = new HashMap<>();
        Map<Long, Integer> noMes = new HashMap<>();
        List<SlotEscala> vagas = new ArrayList<>();
        for (Evento e : eventos) {
            Set<Long> pessoas = naMissa.computeIfAbsent(e.getId(), k -> new HashSet<>());
            for (SlotEscala s : e.getSlots()) {
                if (s.getVoluntario() != null) {
                    pessoas.add(s.getVoluntario().getId());
                    noDia.computeIfAbsent(e.getData(), k -> new HashSet<>()).add(s.getVoluntario().getId());
                    if (s.getSetor() == setor) noMes.merge(s.getVoluntario().getId(), 1, Integer::sum);
                } else if (s.getSetor() == setor) {
                    vagas.add(s);
                }
            }
        }

        Map<Long, List<Usuario>> candidatos = new HashMap<>();
        if (!vagas.isEmpty()) {
            for (CandidaturaEscala c : candidaturaRepository.findBySlotIdIn(vagas.stream().map(SlotEscala::getId).toList())) {
                if (c.getUsuario().fazParteDoSetor(setor)) {
                    candidatos.computeIfAbsent(c.getSlot().getId(), k -> new ArrayList<>()).add(c.getUsuario());
                }
            }
        }

        // Histórico recente do setor: desempata a favor de quem serviu menos / está há mais tempo parado.
        Map<Long, Integer> recente = new HashMap<>();
        Map<Long, LocalDate> ultimaVez = new HashMap<>();
        for (SlotEscala s : slotRepository.escaladosDoSetorEntre(setor, LocalDate.of(2000, 1, 1), hoje)) {
            Long id = s.getVoluntario().getId();
            LocalDate dia = s.getEvento().getData();
            if (!dia.isBefore(hoje.minusMonths(3))) recente.merge(id, 1, Integer::sum);
            ultimaVez.merge(id, dia, (a, b) -> a.isAfter(b) ? a : b);
        }

        List<SlotEscala> ordem = new ArrayList<>(vagas);
        ordem.sort(Comparator.<SlotEscala>comparingInt(s -> candidatos.getOrDefault(s.getId(), List.of()).size())
                .thenComparing(s -> s.getEvento().getData())
                .thenComparing(s -> s.getEvento().getHorario())
                .thenComparing(SlotEscala::getId));

        Map<Long, Map<String, Object>> porSlot = new HashMap<>();
        for (SlotEscala s : ordem) {
            Evento e = s.getEvento();
            Set<Long> ocupados = naMissa.get(e.getId());
            Set<Long> doDia = noDia.getOrDefault(e.getData(), Set.of());
            Usuario escolhido = candidatos.getOrDefault(s.getId(), List.of()).stream()
                    .filter(u -> !ocupados.contains(u.getId()))
                    .min(Comparator.<Usuario>comparingInt(u -> noMes.getOrDefault(u.getId(), 0))
                            .thenComparingInt(u -> doDia.contains(u.getId()) ? 1 : 0)
                            .thenComparingInt(u -> recente.getOrDefault(u.getId(), 0))
                            .thenComparing(u -> ultimaVez.get(u.getId()), Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparing(Usuario::getNome, String.CASE_INSENSITIVE_ORDER))
                    .orElse(null);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eventoId", e.getId());
            item.put("slotId", s.getId());
            item.put("data", e.getData().toString());
            item.put("horario", e.getHorario() == null ? null : e.getHorario().toString());
            item.put("evento", e.getTitulo());
            item.put("funcao", s.getRotulo());
            item.put("candidatos", candidatos.getOrDefault(s.getId(), List.of()).size());
            if (escolhido != null) {
                ocupados.add(escolhido.getId());
                noDia.computeIfAbsent(e.getData(), k -> new HashSet<>()).add(escolhido.getId());
                noMes.merge(escolhido.getId(), 1, Integer::sum);
                item.put("usuarioId", escolhido.getId());
                item.put("nome", escolhido.getNome());
                item.put("serviuRecente", recente.getOrDefault(escolhido.getId(), 0));
            }
            porSlot.put(s.getId(), item);
        }

        // Devolve na ordem da escala (data, horário), não na ordem em que foi resolvido.
        List<Map<String, Object>> itens = vagas.stream()
                .sorted(Comparator.<SlotEscala, LocalDate>comparing(s -> s.getEvento().getData())
                        .thenComparing(s -> s.getEvento().getHorario())
                        .thenComparing(SlotEscala::getId))
                .map(s -> porSlot.get(s.getId())).toList();
        long preenchidas = itens.stream().filter(i -> i.containsKey("usuarioId")).count();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mes", mes.toString());
        r.put("setor", setor);
        r.put("vagas", itens.size());
        r.put("preenchidas", preenchidas);
        r.put("semCandidato", itens.size() - preenchidas);
        r.put("itens", itens);
        return r;
    }

    /** Aplica o que a coordenação deixou marcado. Cada item passa pelas regras normais de escalar. */
    public Map<String, Object> aplicar(Setor setor, List<Map<String, Object>> itens, Usuario solicitante) {
        exigirCoordenacao(setor, solicitante);
        if (itens == null || itens.isEmpty()) throw new IllegalArgumentException("Nada pra aplicar.");
        if (itens.size() > 300) throw new IllegalArgumentException("Itens demais.");
        int feitos = 0;
        List<String> problemas = new ArrayList<>();
        for (Map<String, Object> i : itens) {
            Long eventoId = numero(i.get("eventoId")), slotId = numero(i.get("slotId")), usuarioId = numero(i.get("usuarioId"));
            if (eventoId == null || slotId == null || usuarioId == null) continue;
            SlotEscala slot = slotRepository.findById(slotId).orElse(null);
            if (slot == null || slot.getSetor() != setor) { problemas.add("Uma função não existe mais."); continue; }
            if (slot.getVoluntario() != null) { problemas.add(slot.getRotulo() + ": alguém já foi escalado(a) enquanto isso."); continue; }
            try {
                eventoService.escalar(eventoId, slotId, usuarioId, solicitante);
                feitos++;
            } catch (IllegalStateException | IllegalArgumentException e) {
                problemas.add(e.getMessage());
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("escalados", feitos);
        r.put("problemas", problemas);
        return r;
    }

    private static void exigirCoordenacao(Setor setor, Usuario solicitante) {
        if (setor == null) throw new IllegalArgumentException("Escolha o setor.");
        if (solicitante == null || !solicitante.coordenaEscalaDoSetor(setor)) {
            throw new IllegalStateException("Só a coordenação do setor pode usar a sugestão de escala.");
        }
    }

    private static Long numero(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s && s.matches("\\d+")) return Long.parseLong(s);
        return null;
    }
}
