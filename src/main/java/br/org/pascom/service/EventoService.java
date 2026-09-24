package br.org.pascom.service;

import br.org.pascom.dto.EventoDTO;
import br.org.pascom.dto.EventoRequestDTO;
import br.org.pascom.dto.SlotEscalaDTO;
import br.org.pascom.dto.SlotEscalaRequestDTO;
import br.org.pascom.model.Evento;
import br.org.pascom.model.SlotEscala;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.MotivoExclusao;
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
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class EventoService {

    private final EventoRepository eventoRepository;
    private final SlotEscalaRepository slotEscalaRepository;

    public EventoService(EventoRepository eventoRepository, SlotEscalaRepository slotEscalaRepository) {
        this.eventoRepository = eventoRepository;
        this.slotEscalaRepository = slotEscalaRepository;
    }

    public List<EventoDTO> listarTodos() {
        return eventoRepository.findByExcluidoEmIsNull().stream().map(this::toDTO).toList();
    }

    public List<EventoDTO> listarLixeira() {
        return eventoRepository.findByExcluidoEmIsNotNullOrderByExcluidoEmDesc().stream().map(this::toDTO).toList();
    }

    @Transactional
    public EventoDTO criar(EventoRequestDTO dto, Usuario solicitante) {
        if (!solicitante.temPoderesDeCoordenadorGeral()) {
            throw new IllegalStateException("Apenas o Coordenador Geral pode criar eventos e marcar datas no calendário.");
        }

        Evento evento = Evento.builder()
                .titulo(dto.titulo())
                .data(dto.data())
                .horario(dto.horario())
                .local(dto.local())
                .eventoGrande(dto.eventoGrande() != null && dto.eventoGrande())
                .build();

        if (dto.slots() != null) {
            List<SlotEscala> slots = dto.slots().stream().map(sDto -> SlotEscala.builder()
                    .rotulo(sDto.rotulo())
                    .tipo(sDto.tipo())
                    .evento(evento)
                    .build()).toList();
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

    @Transactional
    public EventoDTO adicionarSlot(Long eventoId, SlotEscalaRequestDTO dto, Usuario solicitante) {
        if (!solicitante.temPoderesDeCoordenadorGeral()) {
            throw new IllegalStateException("Apenas o Coordenador Geral pode adicionar funções a uma escala.");
        }

        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado."));

        SlotEscala slot = SlotEscala.builder()
                .rotulo(dto.rotulo())
                .tipo(dto.tipo())
                .evento(evento)
                .build();
        slotEscalaRepository.save(slot);

        return toDTO(eventoRepository.findById(eventoId).orElseThrow());
    }

    /**
     * Gera automaticamente um evento "Missa das 10h" pra cada domingo do mês informado,
     * às 10h na Igreja Matriz, já com os 3 slots de cobertura padrão (Foto, Vídeo, Stories
     * ao vivo) — sem duplicar se já existir um evento com essa data+título.
     */
    @Transactional
    public int gerarEscalasDominicaisDoMes(YearMonth mes) {
        int criados = 0;
        LocalDate primeiroDomingo = mes.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        for (LocalDate domingo = primeiroDomingo; !domingo.isAfter(mes.atEndOfMonth()); domingo = domingo.plusWeeks(1)) {
            if (eventoRepository.existsByDataAndTituloAndExcluidoEmIsNull(domingo, "Missa das 10h")) {
                continue;
            }
            Evento evento = Evento.builder()
                    .titulo("Missa das 10h")
                    .data(domingo)
                    .horario(LocalTime.of(10, 0))
                    .local("Igreja Matriz")
                    .eventoGrande(false)
                    .build();
            List<SlotEscala> slots = List.of(
                    SlotEscala.builder().rotulo("Foto").tipo(SlotTipo.FOTO).evento(evento).build(),
                    SlotEscala.builder().rotulo("Vídeo").tipo(SlotTipo.VIDEO).evento(evento).build(),
                    SlotEscala.builder().rotulo("Stories ao vivo").tipo(SlotTipo.STORIES_AO_VIVO).evento(evento).build()
            );
            evento.setSlots(slots);
            eventoRepository.save(evento);
            criados++;
        }
        return criados;
    }

    /** Roda no dia 1 de cada mês às 6h: garante que os domingos do mês já têm escala aberta. */
    @Scheduled(cron = "0 0 6 1 * *")
    public void gerarEscalasDoMesAgendado() {
        try {
            gerarEscalasDominicaisDoMes(YearMonth.now());
        } catch (Exception ignorada) {
            // não deve travar a aplicação; dá pra gerar manualmente depois
        }
    }

    @Transactional
    public EventoDTO desinscreverVoluntario(Long eventoId, Long slotId) {
        if (!eventoRepository.existsById(eventoId)) {
            throw new IllegalArgumentException("Evento não encontrado.");
        }

        SlotEscala slot = slotEscalaRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot de escala não encontrado."));

        if (!slot.getEvento().getId().equals(eventoId)) {
            throw new IllegalArgumentException("Esse slot não pertence a este evento.");
        }

        slot.setVoluntario(null);
        slotEscalaRepository.save(slot);

        return toDTO(eventoRepository.findById(eventoId).orElseThrow());
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

    private EventoDTO toDTO(Evento e) {
        List<SlotEscalaDTO> slotsDto = e.getSlots().stream()
                .map(s -> new SlotEscalaDTO(
                        s.getId(),
                        s.getRotulo(),
                        s.getTipo(),
                        s.getVoluntario() != null ? s.getVoluntario().getId() : null,
                        s.getVoluntario() != null ? s.getVoluntario().getNome() : null
                )).toList();

        return new EventoDTO(
                e.getId(), e.getTitulo(), e.getData(), e.getHorario(),
                e.getLocal(), e.getEventoGrande(), slotsDto,
                e.getExcluidoEm(), e.getMotivoExclusao(), e.getDetalheExclusao()
        );
    }
}