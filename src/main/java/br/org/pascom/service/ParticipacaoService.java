package br.org.pascom.service;

import br.org.pascom.model.CandidaturaEscala;
import br.org.pascom.model.SlotEscala;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Role;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.repository.CandidaturaEscalaRepository;
import br.org.pascom.repository.SlotEscalaRepository;
import br.org.pascom.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Relatório de participação pra coordenação equilibrar a escala: quantas vezes cada pessoa do setor
 * serviu no período, quando foi a última vez, o que já tem marcado e onde se candidatou. Substitui o
 * controle que a coordenação do Datashow fazia à mão.
 */
@Service
public class ParticipacaoService {

    /** Quem passou disso sem servir aparece destacado ("há muito tempo sem servir"). */
    public static final int DIAS_ALERTA = 45;

    private final SlotEscalaRepository slotRepository;
    private final CandidaturaEscalaRepository candidaturaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CalendarioService calendarioService;

    public ParticipacaoService(SlotEscalaRepository slotRepository, CandidaturaEscalaRepository candidaturaRepository,
                               UsuarioRepository usuarioRepository, CalendarioService calendarioService) {
        this.slotRepository = slotRepository;
        this.candidaturaRepository = candidaturaRepository;
        this.usuarioRepository = usuarioRepository;
        this.calendarioService = calendarioService;
    }

    /** @param meses janela pra trás a partir de hoje (1 a 24); 0 = desde o começo. */
    @Transactional(readOnly = true)
    public Map<String, Object> doSetor(Setor setor, int meses, Usuario solicitante) {
        if (setor == null) throw new IllegalArgumentException("Escolha o setor.");
        if (solicitante == null || !solicitante.coordenaEscalaDoSetor(setor)) {
            throw new IllegalStateException("O relatório de participação é só da coordenação do setor.");
        }
        if (meses < 0 || meses > 24) throw new IllegalArgumentException("Período inválido.");
        LocalDate hoje = calendarioService.hoje();
        LocalDate inicio = meses == 0 ? LocalDate.of(2000, 1, 1) : hoje.minusMonths(meses);

        Map<Long, Linha> linhas = new LinkedHashMap<>();
        // Todo mundo do setor aparece, inclusive quem nunca serviu (é justamente quem a coordenação quer achar).
        // A Coordenação Geral "faz parte" de todos os setores, então só entra se de fato serviu.
        for (Usuario u : usuarioRepository.findAll()) {
            if (u.getRole() != Role.COORDENADOR_GERAL && u.fazParteDoSetor(setor)) linhas.put(u.getId(), new Linha(u));
        }
        // Todo o histórico (a "última vez" não depende do período escolhido), mais os próximos 2 meses (já marcado).
        for (SlotEscala s : slotRepository.escaladosDoSetorEntre(setor, LocalDate.of(2000, 1, 1), hoje.plusMonths(2))) {
            LocalDate dia = s.getEvento().getData();
            Linha l = linhas.get(s.getVoluntario().getId());
            if (l == null) {
                // Já saiu do setor: só aparece se serviu dentro do período.
                if (dia.isBefore(inicio) || dia.isAfter(hoje)) continue;
                l = new Linha(s.getVoluntario());
                linhas.put(l.id, l);
            }
            if (!dia.isAfter(hoje)) {
                if (!dia.isBefore(inicio)) l.serviu++;
                if (l.ultimaVez == null || dia.isAfter(l.ultimaVez)) l.ultimaVez = dia;
            } else {
                l.proximas++;
                if (l.proximaVez == null || dia.isBefore(l.proximaVez)) l.proximaVez = dia;
            }
        }
        for (CandidaturaEscala c : candidaturaRepository.doSetorDesde(setor, hoje)) {
            Linha l = linhas.get(c.getUsuario().getId());
            if (l != null && (c.getSlot().getVoluntario() == null || !c.getSlot().getVoluntario().getId().equals(l.id))) l.candidaturas++;
        }

        List<Map<String, Object>> pessoas = new ArrayList<>();
        int total = 0;
        for (Linha l : linhas.values()) {
            total += l.serviu;
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", l.id);
            o.put("nome", l.nome);
            o.put("serviu", l.serviu);
            o.put("ultimaVez", l.ultimaVez == null ? null : l.ultimaVez.toString());
            o.put("diasSemServir", l.ultimaVez == null ? null : ChronoUnit.DAYS.between(l.ultimaVez, hoje));
            o.put("proximas", l.proximas);
            o.put("proximaVez", l.proximaVez == null ? null : l.proximaVez.toString());
            o.put("candidaturas", l.candidaturas);
            pessoas.add(o);
        }
        // Padrão: quem menos serviu primeiro (é quem a coordenação quer chamar); empate, quem está há mais tempo parado.
        pessoas.sort(Comparator.<Map<String, Object>>comparingInt(o -> (int) o.get("serviu"))
                .thenComparing(o -> (String) o.get("ultimaVez"), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(o -> (String) o.get("nome"), String.CASE_INSENSITIVE_ORDER));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("setor", setor);
        r.put("meses", meses);
        r.put("inicio", meses == 0 ? null : inicio.toString());
        r.put("hoje", hoje.toString());
        r.put("totalServicos", total);
        r.put("media", pessoas.isEmpty() ? 0 : Math.round(total * 10.0 / pessoas.size()) / 10.0);
        r.put("diasAlerta", DIAS_ALERTA);
        r.put("pessoas", pessoas);
        return r;
    }

    private static final class Linha {
        final Long id;
        final String nome;
        int serviu, proximas, candidaturas;
        LocalDate ultimaVez, proximaVez;

        Linha(Usuario u) {
            this.id = u.getId();
            this.nome = u.getNome();
        }
    }
}
