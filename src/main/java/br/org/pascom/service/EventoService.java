package br.org.pascom.service;

import br.org.pascom.dto.EventoDTO;
import br.org.pascom.dto.EventoRequestDTO;
import br.org.pascom.dto.SlotEscalaDTO;
import br.org.pascom.model.Evento;
import br.org.pascom.model.SlotEscala;
import br.org.pascom.model.Usuario;
import br.org.pascom.repository.EventoRepository;
import br.org.pascom.repository.SlotEscalaRepository;
import br.org.pascom.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EventoService {

    private final EventoRepository eventoRepository;
    private final SlotEscalaRepository slotEscalaRepository;
    private final UsuarioRepository usuarioRepository;

    public EventoService(EventoRepository eventoRepository, SlotEscalaRepository slotEscalaRepository, UsuarioRepository usuarioRepository) {
        this.eventoRepository = eventoRepository;
        this.slotEscalaRepository = slotEscalaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<EventoDTO> listarTodos() {
        return eventoRepository.findAll().stream().map(this::toDTO).toList();
    }

    @Transactional
    public EventoDTO criar(EventoRequestDTO dto) {
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
    public EventoDTO inscreverVoluntario(Long eventoId, Long slotId, Long voluntarioId) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado."));

        Usuario voluntario = usuarioRepository.findById(voluntarioId)
                .orElseThrow(() -> new IllegalArgumentException("Voluntário não encontrado."));

        boolean jaInscrito = evento.getSlots().stream()
                .anyMatch(s -> s.getVoluntario() != null && s.getVoluntario().getId().equals(voluntarioId));

        if (jaInscrito) {
            throw new IllegalStateException("Você já está escalado para este evento.");
        }

        SlotEscala slot = slotEscalaRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot de escala não encontrado."));

        slot.setVoluntario(voluntario);
        slotEscalaRepository.save(slot);

        return toDTO(eventoRepository.findById(eventoId).orElseThrow());
    }

    @Transactional
    public EventoDTO desinscreverVoluntario(Long eventoId, Long slotId) {
        SlotEscala slot = slotEscalaRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot de escala não encontrado."));

        slot.setVoluntario(null);
        slotEscalaRepository.save(slot);

        return toDTO(eventoRepository.findById(eventoId).orElseThrow());
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
                e.getLocal(), e.getEventoGrande(), slotsDto
        );
    }
}