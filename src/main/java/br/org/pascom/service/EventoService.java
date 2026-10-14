package br.org.pascom.service;

import br.org.pascom.dto.EscalaCompletaDTO;
import br.org.pascom.dto.EventoDTO;
import br.org.pascom.dto.EventoRequestDTO;
import br.org.pascom.dto.SlotEscalaDTO;
import br.org.pascom.dto.SlotEscalaRequestDTO;
import br.org.pascom.model.Evento;
import br.org.pascom.model.SlotEscala;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.MotivoExclusao;
import br.org.pascom.model.enums.Role;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.model.enums.SlotTipo;
import br.org.pascom.repository.EventoRepository;
import br.org.pascom.repository.SlotEscalaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class EventoService {

    public static final String TITULO_MISSA_DOMINICAL = "Missa das 10h";
    public static final LocalTime HORARIO_MISSA_DOMINICAL = LocalTime.of(10, 0);
    public static final String LOCAL_PADRAO = "Igreja Matriz";

    /** Funções que toda Missa das 10h de domingo já nasce tendo, separadas por setor. */
    private record FuncaoPadrao(Setor setor, String rotulo, SlotTipo tipo) {}

    private static final List<FuncaoPadrao> FUNCOES_PADRAO_DOMINGO = List.of(
            new FuncaoPadrao(Setor.REDES_SOCIAIS, "Foto", SlotTipo.FOTO),
            new FuncaoPadrao(Setor.REDES_SOCIAIS, "Vídeo", SlotTipo.VIDEO),
            new FuncaoPadrao(Setor.REDES_SOCIAIS, "Stories ao vivo", SlotTipo.STORIES_AO_VIVO),
            new FuncaoPadrao(Setor.TRANSMISSOES_VIDEOS, "Transmissão", SlotTipo.TRANSMISSAO),
            new FuncaoPadrao(Setor.DATASHOW, "Datashow", null)
    );

    private final EventoRepository eventoRepository;
    private final SlotEscalaRepository slotEscalaRepository;
    private final CalendarioService calendarioService;

    public EventoService(EventoRepository eventoRepository, SlotEscalaRepository slotEscalaRepository,
                         CalendarioService calendarioService) {
        this.eventoRepository = eventoRepository;
        this.slotEscalaRepository = slotEscalaRepository;
        this.calendarioService = calendarioService;
    }

    public List<EventoDTO> listarTodos() {
        return eventoRepository.findByExcluidoEmIsNull().stream().map(this::toDTO).toList();
    }

    public List<EventoDTO> listarLixeira() {
        return eventoRepository.findByExcluidoEmIsNotNullOrderByExcluidoEmDesc().stream().map(this::toDTO).toList();
    }

    /**
     * Qualquer coordenador (de setor ou geral) pode marcar um evento extra que precisa de escala.
     * Coordenador de setor só cria funções do próprio setor; os outros setores entram depois,
     * cada coordenação adicionando as suas funções pelo {@link #adicionarSlot}.
     */
    @Transactional
    public EventoDTO criar(EventoRequestDTO dto, Usuario solicitante) {
        if (!solicitante.isCoordenacao()) {
            throw new IllegalStateException("Apenas coordenadores podem criar eventos e marcar datas na escala.");
        }

        Evento evento = Evento.builder()
                .titulo(dto.titulo())
                .data(dto.data())
                .horario(dto.horario())
                .local(dto.local())
                .eventoGrande(dto.eventoGrande() != null && dto.eventoGrande())
                .build();

        if (dto.slots() != null) {
            List<SlotEscala> slots = new ArrayList<>();
            for (SlotEscalaRequestDTO sDto : dto.slots()) {
                slots.add(SlotEscala.builder()
                        .rotulo(sDto.rotulo().trim())
                        .tipo(sDto.tipo())
                        .setor(setorPermitido(sDto.setor(), solicitante))
                        .evento(evento)
                        .build());
            }
            evento.setSlots(slots);
        }

        return toDTO(eventoRepository.save(evento));
    }

    @Transactional
    public EventoDTO inscreverVoluntario(Long eventoId, Long slotId, Usuario voluntario) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado."));

        SlotEscala slot = slotEscalaRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot de escala não encontrado."));

        if (!slot.getEvento().getId().equals(eventoId)) {
            throw new IllegalArgumentException("Esse slot não pertence a este evento.");
        }

        if (slot.getVoluntario() != null) {
            throw new IllegalStateException("Esse horário já foi preenchido por outro voluntário.");
        }

        boolean jaInscrito = evento.getSlots().stream()
                .anyMatch(s -> s.getVoluntario() != null && s.getVoluntario().getId().equals(voluntario.getId()));

        if (jaInscrito) {
            throw new IllegalStateException("Você já está escalado para este evento.");
        }

        slot.setVoluntario(voluntario);
        slotEscalaRepository.save(slot);

        return toDTO(eventoRepository.findById(eventoId).orElseThrow());
    }

    /** Coordenador de setor só adiciona funções do próprio setor; Coordenador Geral, de qualquer setor. */
    @Transactional
    public EventoDTO adicionarSlot(Long eventoId, SlotEscalaRequestDTO dto, Usuario solicitante) {
        if (!solicitante.isCoordenacao()) {
            throw new IllegalStateException("Apenas coordenadores podem adicionar funções a uma escala.");
        }

        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado."));

        SlotEscala slot = SlotEscala.builder()
                .rotulo(dto.rotulo().trim())
                .tipo(dto.tipo())
                .setor(setorPermitido(dto.setor(), solicitante))
                .evento(evento)
                .build();
        slotEscalaRepository.save(slot);

        return toDTO(eventoRepository.findById(eventoId).orElseThrow());
    }

    /**
     * Quem pode tirar alguém de uma escala:
     * - a própria pessoa (sair da escala em que se inscreveu);
     * - o Coordenador Geral, qualquer pessoa;
     * - o coordenador de um setor, só pessoas da equipe dele (mesmo setor).
     */
    @Transactional
    public EventoDTO desinscreverVoluntario(Long eventoId, Long slotId, Usuario solicitante) {
        if (!eventoRepository.existsById(eventoId)) {
            throw new IllegalArgumentException("Evento não encontrado.");
        }

        SlotEscala slot = slotEscalaRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot de escala não encontrado."));

        if (!slot.getEvento().getId().equals(eventoId)) {
            throw new IllegalArgumentException("Esse slot não pertence a este evento.");
        }

        Usuario escalado = slot.getVoluntario();
        if (escalado == null) {
            throw new IllegalStateException("Essa vaga já está livre.");
        }

        boolean ehElaMesma = escalado.getId().equals(solicitante.getId());
        boolean coordenaEquipeDela = solicitante.temPoderesDeCoordenadorGeral()
                || (solicitante.getRole() == Role.COORDENADOR && solicitante.getSetor() != null
                    && solicitante.getSetor() == escalado.getSetor());

        if (!ehElaMesma && !coordenaEquipeDela) {
            throw new IllegalStateException(solicitante.getRole() == Role.COORDENADOR
                    ? "Você só pode tirar da escala pessoas da sua equipe."
                    : "Apenas coordenadores podem tirar outra pessoa da escala.");
        }

        slot.setVoluntario(null);
        slotEscalaRepository.save(slot);

        return toDTO(eventoRepository.findById(eventoId).orElseThrow());
    }

    /**
     * Gera automaticamente um evento "Missa das 10h" pra cada domingo do mês informado (datas
     * vindas do {@link CalendarioService}), já com as funções padrão de cada setor — sem duplicar
     * se já existir. Para os domingos que já tinham evento (e ainda não passaram), completa as
     * funções padrão que estiverem faltando (ex.: eventos antigos, criados antes da escala por setor).
     *
     * @return quantos eventos novos foram criados
     */
    @Transactional
    public int gerarEscalasDominicaisDoMes(YearMonth mes) {
        int criados = 0;
        LocalDate hoje = calendarioService.hoje();
        for (LocalDate domingo : calendarioService.domingosDoMes(mes)) {
            Evento existente = eventoRepository
                    .findFirstByDataAndTituloAndExcluidoEmIsNull(domingo, TITULO_MISSA_DOMINICAL)
                    .orElse(null);
            if (existente != null) {
                if (!domingo.isBefore(hoje) && completarFuncoesPadrao(existente)) {
                    eventoRepository.save(existente);
                }
                continue;
            }
            Evento evento = Evento.builder()
                    .titulo(TITULO_MISSA_DOMINICAL)
                    .data(domingo)
                    .horario(HORARIO_MISSA_DOMINICAL)
                    .local(LOCAL_PADRAO)
                    .eventoGrande(false)
                    .build();
            completarFuncoesPadrao(evento);
            eventoRepository.save(evento);
            criados++;
        }
        return criados;
    }

    /** Adiciona ao evento as funções padrão de domingo que ainda não existem nele. Devolve true se mudou algo. */
    private boolean completarFuncoesPadrao(Evento evento) {
        boolean mudou = false;
        for (FuncaoPadrao padrao : FUNCOES_PADRAO_DOMINGO) {
            boolean jaTem = evento.getSlots().stream().anyMatch(s ->
                    s.getRotulo() != null && s.getRotulo().equalsIgnoreCase(padrao.rotulo())
                            && (s.getSetor() == null || s.getSetor() == padrao.setor()));
            if (!jaTem) {
                evento.getSlots().add(SlotEscala.builder()
                        .rotulo(padrao.rotulo())
                        .tipo(padrao.tipo())
                        .setor(padrao.setor())
                        .evento(evento)
                        .build());
                mudou = true;
            }
        }
        return mudou;
    }

    /**
     * Mantém a escala sempre "renovada": garante os domingos do mês atual e do próximo (pra
     * quem quiser se inscrever com antecedência no fim do mês). Usado pelo agendamento e
     * também na inicialização da aplicação, caso o servidor estivesse fora do ar no dia 1.
     */
    @Transactional
    public int garantirEscalasDoMesAtualEProximo() {
        YearMonth atual = YearMonth.from(calendarioService.hoje());
        return gerarEscalasDominicaisDoMes(atual) + gerarEscalasDominicaisDoMes(atual.plusMonths(1));
    }

    /** Roda no dia 1 de cada mês às 6h (horário de Brasília). */
    @Scheduled(cron = "0 0 6 1 * *", zone = CalendarioService.FUSO)
    @Transactional
    public void gerarEscalasDoMesAgendado() {
        try {
            garantirEscalasDoMesAtualEProximo();
        } catch (Exception ignorada) {
            // não deve travar a aplicação; dá pra gerar manualmente depois
        }
    }

    /** Escala completa do mês: todos os eventos com escala, com as funções agrupadas por setor. */
    @Transactional(readOnly = true)
    public EscalaCompletaDTO escalaCompleta(YearMonth mes) {
        List<Evento> eventos = eventoRepository.findByDataBetweenAndExcluidoEmIsNullOrderByDataAscHorarioAsc(
                mes.atDay(1), mes.atEndOfMonth());

        List<EscalaCompletaDTO.EventoEscala> itens = eventos.stream()
                .filter(e -> !e.getSlots().isEmpty())
                .map(this::toEventoEscala)
                .toList();

        return new EscalaCompletaDTO(mes.toString(), calendarioService.domingosDoMes(mes), itens);
    }

    private EscalaCompletaDTO.EventoEscala toEventoEscala(Evento e) {
        List<EscalaCompletaDTO.GrupoSetor> grupos = new ArrayList<>();
        // Ordem fixa: setores na ordem do enum, e as funções gerais (sem setor) por último.
        List<Setor> ordem = new ArrayList<>(List.of(Setor.values()));
        ordem.add(null);
        for (Setor setor : ordem) {
            List<SlotEscalaDTO> funcoes = e.getSlots().stream()
                    .filter(s -> Objects.equals(s.getSetor(), setor))
                    .sorted(Comparator.comparing(SlotEscala::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(this::toSlotDTO)
                    .toList();
            if (!funcoes.isEmpty()) {
                grupos.add(new EscalaCompletaDTO.GrupoSetor(setor, nomeSetor(setor), funcoes));
            }
        }
        int vagas = (int) e.getSlots().stream().filter(s -> s.getVoluntario() == null).count();
        return new EscalaCompletaDTO.EventoEscala(
                e.getId(), e.getTitulo(), e.getData(), e.getHorario(), e.getLocal(),
                Boolean.TRUE.equals(e.getEventoGrande()),
                calendarioService.ehDomingo(e.getData()),
                vagas, grupos);
    }

    public static String nomeSetor(Setor setor) {
        if (setor == null) return "Geral";
        return switch (setor) {
            case REDES_SOCIAIS -> "Redes sociais";
            case JOVENS -> "Jovens";
            case DATASHOW -> "Datashow";
            case TRANSMISSOES_VIDEOS -> "Transmissões/Vídeos";
        };
    }

    /**
     * Setor que de fato vai ser gravado numa função nova: coordenador de setor sempre grava o
     * próprio setor (não dá pra criar função pra equipe dos outros); Coordenador Geral grava o
     * que pediu (null = função geral).
     */
    private Setor setorPermitido(Setor pedido, Usuario solicitante) {
        if (solicitante.temPoderesDeCoordenadorGeral()) return pedido;
        return solicitante.getSetor();
    }

    @Transactional
    public EventoDTO moverParaLixeira(Long id, MotivoExclusao motivo, String detalhe, Usuario solicitante) {
        if (!solicitante.temPoderesDeCoordenadorGeral()) {
            throw new IllegalStateException("Apenas o Coordenador Geral pode excluir eventos da escala.");
        }
        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado."));
        evento.setExcluidoEm(LocalDateTime.now());
        evento.setMotivoExclusao(motivo);
        evento.setDetalheExclusao(detalhe);
        return toDTO(eventoRepository.save(evento));
    }

    @Transactional
    public EventoDTO restaurar(Long id, Usuario solicitante) {
        if (!solicitante.temPoderesDeCoordenadorGeral()) {
            throw new IllegalStateException("Apenas o Coordenador Geral pode restaurar eventos da lixeira.");
        }
        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado."));
        if (evento.getExcluidoEm() == null) {
            throw new IllegalStateException("Este evento não está na lixeira.");
        }
        evento.setExcluidoEm(null);
        evento.setMotivoExclusao(null);
        evento.setDetalheExclusao(null);
        return toDTO(eventoRepository.save(evento));
    }

    private SlotEscalaDTO toSlotDTO(SlotEscala s) {
        return new SlotEscalaDTO(
                s.getId(),
                s.getRotulo(),
                s.getTipo(),
                s.getVoluntario() != null ? s.getVoluntario().getId() : null,
                s.getVoluntario() != null ? s.getVoluntario().getNome() : null,
                s.getSetor()
        );
    }

    private EventoDTO toDTO(Evento e) {
        List<SlotEscalaDTO> slotsDto = e.getSlots().stream().map(this::toSlotDTO).toList();

        return new EventoDTO(
                e.getId(), e.getTitulo(), e.getData(), e.getHorario(),
                e.getLocal(), e.getEventoGrande(), slotsDto,
                e.getExcluidoEm(), e.getMotivoExclusao(), e.getDetalheExclusao()
        );
    }
}
