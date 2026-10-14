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

import java.time.DayOfWeek;
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
    public static final String TITULO_MISSA_SABADO = "Missa das 16h";
    public static final LocalTime HORARIO_MISSA_SABADO = LocalTime.of(16, 0);
    public static final String LOCAL_PADRAO = "Igreja Matriz";

    /**
     * Versão atual do modelo de funções das missas automáticas. Subir esse número quando
     * {@link #MISSAS_AUTOMATICAS} mudar: os eventos futuros com versão menor são atualizados uma
     * única vez pelo {@link #gerarEscalasDoMes}; depois disso, as edições da coordenação valem.
     * Versão 2 = Transmissões com 3 câmeras + corte + auxiliar de corte (antes: uma "Transmissão"
     * só) e a Missa das 16h de sábado só com Datashow.
     */
    static final int VERSAO_FUNCOES_PADRAO = 2;

    /** Função que uma missa automática já nasce tendo. */
    private record FuncaoPadrao(Setor setor, String rotulo, SlotTipo tipo) {}

    /** Missa que entra sozinha na escala todo mês, num dia da semana fixo, com as funções padrão dela. */
    private record MissaAutomatica(DayOfWeek dia, String titulo, LocalTime horario, List<FuncaoPadrao> funcoes) {}

    private static final List<MissaAutomatica> MISSAS_AUTOMATICAS = List.of(
            new MissaAutomatica(DayOfWeek.SUNDAY, TITULO_MISSA_DOMINICAL, HORARIO_MISSA_DOMINICAL, List.of(
                    new FuncaoPadrao(Setor.REDES_SOCIAIS, "Foto", SlotTipo.FOTO),
                    new FuncaoPadrao(Setor.REDES_SOCIAIS, "Vídeo", SlotTipo.VIDEO),
                    new FuncaoPadrao(Setor.REDES_SOCIAIS, "Stories ao vivo", SlotTipo.STORIES_AO_VIVO),
                    new FuncaoPadrao(Setor.TRANSMISSOES_VIDEOS, "Câmera 1", null),
                    new FuncaoPadrao(Setor.TRANSMISSOES_VIDEOS, "Câmera 2", null),
                    new FuncaoPadrao(Setor.TRANSMISSOES_VIDEOS, "Câmera 3", null),
                    new FuncaoPadrao(Setor.TRANSMISSOES_VIDEOS, "Corte da transmissão", SlotTipo.TRANSMISSAO),
                    new FuncaoPadrao(Setor.TRANSMISSOES_VIDEOS, "Auxiliar de corte", null),
                    new FuncaoPadrao(Setor.DATASHOW, "Datashow", null))),
            // Sábado só o Datashow opera.
            new MissaAutomatica(DayOfWeek.SATURDAY, TITULO_MISSA_SABADO, HORARIO_MISSA_SABADO, List.of(
                    new FuncaoPadrao(Setor.DATASHOW, "Datashow", null)))
    );

    /** Função única de transmissão do modelo antigo (versão 1), trocada pelas câmeras + corte. */
    private static final String ROTULO_TRANSMISSAO_ANTIGO = "Transmissão";

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

        if (slotEscalaRepository.ocuparSeLivre(slotId, voluntario) == 0) {
            throw new IllegalStateException("Alguém acabou de pegar essa vaga. Escolha outra.");
        }

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
     * Renomeia uma função da escala. Coordenador Geral mexe em qualquer função; coordenador de
     * setor, só nas do próprio setor (funções gerais, sem setor, só o Coordenador Geral).
     */
    @Transactional
    public EventoDTO renomearSlot(Long eventoId, Long slotId, String rotulo, Usuario solicitante) {
        SlotEscala slot = slotDoEvento(eventoId, slotId);
        exigirPodeEditarFuncao(slot, solicitante);
        if (rotulo == null || rotulo.isBlank()) {
            throw new IllegalArgumentException("Dê um nome pra função.");
        }
        slot.setRotulo(rotulo.trim());
        slotEscalaRepository.save(slot);
        return toDTO(eventoRepository.findById(eventoId).orElseThrow());
    }

    /** Remove uma função da escala (mesma regra de permissão do {@link #renomearSlot}). */
    @Transactional
    public EventoDTO removerSlot(Long eventoId, Long slotId, Usuario solicitante) {
        SlotEscala slot = slotDoEvento(eventoId, slotId);
        exigirPodeEditarFuncao(slot, solicitante);
        Evento evento = slot.getEvento();
        evento.getSlots().remove(slot);
        eventoRepository.save(evento);
        return toDTO(eventoRepository.findById(eventoId).orElseThrow());
    }

    private SlotEscala slotDoEvento(Long eventoId, Long slotId) {
        SlotEscala slot = slotEscalaRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Função não encontrada."));
        if (!slot.getEvento().getId().equals(eventoId)) {
            throw new IllegalArgumentException("Essa função não pertence a este evento.");
        }
        return slot;
    }

    private void exigirPodeEditarFuncao(SlotEscala slot, Usuario solicitante) {
        if (solicitante.temPoderesDeCoordenadorGeral()) return;
        if (solicitante.getRole() != Role.COORDENADOR) {
            throw new IllegalStateException("Apenas coordenadores podem alterar as funções da escala.");
        }
        if (slot.getSetor() == null || slot.getSetor() != solicitante.getSetor()) {
            throw new IllegalStateException("Você só pode alterar as funções do seu setor.");
        }
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
        // "Equipe dela" inclui quem entrou no setor do coordenador como setor extra, pelo perfil.
        boolean coordenaEquipeDela = solicitante.temPoderesDeCoordenadorGeral()
                || (solicitante.getRole() == Role.COORDENADOR && escalado.fazParteDoSetor(solicitante.getSetor()));

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
     * Gera as missas automáticas do mês (Missa das 10h de cada domingo e Missa das 16h de cada
     * sábado, datas do {@link CalendarioService}), cada uma já com as funções padrão e sem
     * ninguém escalado: todo mês começa uma escala nova, limpa. Não duplica: se a missa já existe
     * (e ainda não passou) mas está num modelo de funções antigo, atualiza as funções uma única vez.
     *
     * @return quantos eventos novos foram criados
     */
    @Transactional
    public int gerarEscalasDoMes(YearMonth mes) {
        int criados = 0;
        LocalDate hoje = calendarioService.hoje();
        for (MissaAutomatica missa : MISSAS_AUTOMATICAS) {
            for (LocalDate dia : calendarioService.diasDaSemanaDoMes(mes, missa.dia())) {
                // ExcluidoEmIsNull de propósito: missa que foi pra lixeira não impede gerar de novo.
                Evento existente = eventoRepository
                        .findFirstByDataAndTituloAndExcluidoEmIsNull(dia, missa.titulo())
                        .orElse(null);
                if (existente != null) {
                    if (!dia.isBefore(hoje) && atualizarModeloDeFuncoes(existente, missa)) {
                        eventoRepository.save(existente);
                    }
                    continue;
                }
                // Não cria missa em data que já passou (ex.: os primeiros sábados do mês em que
                // a missa de sábado entrou na escala automática).
                if (dia.isBefore(hoje)) continue;
                Evento evento = Evento.builder()
                        .titulo(missa.titulo())
                        .data(dia)
                        .horario(missa.horario())
                        .local(LOCAL_PADRAO)
                        .eventoGrande(false)
                        .versaoFuncoesPadrao(VERSAO_FUNCOES_PADRAO)
                        .build();
                for (FuncaoPadrao padrao : missa.funcoes()) adicionarFuncao(evento, padrao);
                eventoRepository.save(evento);
                criados++;
            }
        }
        return criados;
    }

    /**
     * Leva uma missa já existente pro modelo de funções atual, uma vez só. Tira a "Transmissão"
     * única do modelo antigo (só se ninguém estiver nela) e acrescenta as funções padrão que
     * faltarem. Eventos já na versão atual não são tocados, pra não desfazer o que a coordenação
     * editou ou removeu. Devolve true se mudou algo.
     */
    private boolean atualizarModeloDeFuncoes(Evento evento, MissaAutomatica missa) {
        Integer versao = evento.getVersaoFuncoesPadrao();
        if (versao != null && versao >= VERSAO_FUNCOES_PADRAO) return false;

        evento.getSlots().removeIf(s -> s.getSetor() == Setor.TRANSMISSOES_VIDEOS
                && s.getVoluntario() == null
                && ROTULO_TRANSMISSAO_ANTIGO.equalsIgnoreCase(s.getRotulo() == null ? "" : s.getRotulo().trim()));
        for (FuncaoPadrao padrao : missa.funcoes()) {
            boolean jaTem = evento.getSlots().stream().anyMatch(s ->
                    s.getRotulo() != null && s.getRotulo().trim().equalsIgnoreCase(padrao.rotulo())
                            && (s.getSetor() == null || s.getSetor() == padrao.setor()));
            if (!jaTem) adicionarFuncao(evento, padrao);
        }
        evento.setVersaoFuncoesPadrao(VERSAO_FUNCOES_PADRAO);
        return true;
    }

    private void adicionarFuncao(Evento evento, FuncaoPadrao padrao) {
        evento.getSlots().add(SlotEscala.builder()
                .rotulo(padrao.rotulo())
                .tipo(padrao.tipo())
                .setor(padrao.setor())
                .evento(evento)
                .build());
    }

    /**
     * Mantém a escala sempre "renovada": garante as missas automáticas do mês atual e do próximo (pra
     * quem quiser se inscrever com antecedência no fim do mês). Usado pelo agendamento e
     * também na inicialização da aplicação, caso o servidor estivesse fora do ar no dia 1.
     */
    @Transactional
    public int garantirEscalasDoMesAtualEProximo() {
        YearMonth atual = YearMonth.from(calendarioService.hoje());
        return gerarEscalasDoMes(atual) + gerarEscalasDoMes(atual.plusMonths(1));
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
        return escalaCompleta(mes, null);
    }

    /**
     * Escala do mês. Com {@code setor} preenchido, vira a escala só daquele setor: cada evento
     * mostra as funções do setor e as funções gerais (as mesmas que aparecem na aba "Escala de
     * <setor>" do front), e eventos sem nenhuma dessas funções ficam de fora.
     */
    public EscalaCompletaDTO escalaCompleta(YearMonth mes, Setor setor) {
        List<Evento> eventos = eventoRepository.findByDataBetweenAndExcluidoEmIsNullOrderByDataAscHorarioAsc(
                mes.atDay(1), mes.atEndOfMonth());

        List<Setor> ordem = new ArrayList<>();
        if (setor == null) {
            ordem.addAll(List.of(Setor.values()));
        } else {
            ordem.add(setor);
        }
        // Funções gerais (sem setor) sempre por último.
        ordem.add(null);

        List<EscalaCompletaDTO.EventoEscala> itens = eventos.stream()
                .map(e -> toEventoEscala(e, ordem))
                .filter(e -> !e.setores().isEmpty())
                .toList();

        return new EscalaCompletaDTO(mes.toString(), setor, setor == null ? null : nomeSetor(setor),
                calendarioService.domingosDoMes(mes), itens);
    }

    private EscalaCompletaDTO.EventoEscala toEventoEscala(Evento e, List<Setor> ordem) {
        List<EscalaCompletaDTO.GrupoSetor> grupos = new ArrayList<>();
        int vagas = 0;
        for (Setor setor : ordem) {
            List<SlotEscalaDTO> funcoes = e.getSlots().stream()
                    .filter(s -> Objects.equals(s.getSetor(), setor))
                    .sorted(Comparator.comparing(SlotEscala::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(this::toSlotDTO)
                    .toList();
            if (!funcoes.isEmpty()) {
                grupos.add(new EscalaCompletaDTO.GrupoSetor(setor, nomeSetor(setor), funcoes));
                vagas += (int) funcoes.stream().filter(f -> f.voluntarioId() == null).count();
            }
        }
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
