package br.org.pascom.service;

import br.org.pascom.dto.EscalaCompletaDTO;
import br.org.pascom.dto.EventoDTO;
import br.org.pascom.dto.EventoRequestDTO;
import br.org.pascom.dto.SlotEscalaDTO;
import br.org.pascom.dto.SlotEscalaRequestDTO;
import br.org.pascom.dto.UsuarioResponseDTO;
import br.org.pascom.model.AlteracaoEscala;
import br.org.pascom.repository.AlteracaoEscalaRepository;
import br.org.pascom.dto.UsuarioResumoDTO;
import br.org.pascom.model.CandidaturaEscala;
import br.org.pascom.model.Evento;
import br.org.pascom.model.PublicacaoEscala;
import br.org.pascom.model.SlotEscala;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.MotivoExclusao;
import br.org.pascom.model.enums.Role;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.model.enums.SlotTipo;
import br.org.pascom.repository.CandidaturaEscalaRepository;
import br.org.pascom.repository.EventoRepository;
import br.org.pascom.repository.PublicacaoEscalaRepository;
import br.org.pascom.repository.SlotEscalaRepository;
import br.org.pascom.repository.UsuarioRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
            // Nas outras missas de domingo e no sábado, só o Datashow opera.
            new MissaAutomatica(DayOfWeek.SUNDAY, "Missa das 8h", LocalTime.of(8, 0), List.of(
                    new FuncaoPadrao(Setor.DATASHOW, "Datashow", null))),
            new MissaAutomatica(DayOfWeek.SUNDAY, "Missa das 18h30", LocalTime.of(18, 30), List.of(
                    new FuncaoPadrao(Setor.DATASHOW, "Datashow", null))),
            new MissaAutomatica(DayOfWeek.SATURDAY, TITULO_MISSA_SABADO, HORARIO_MISSA_SABADO, List.of(
                    new FuncaoPadrao(Setor.DATASHOW, "Datashow", null)))
    );

    /** Função única de transmissão do modelo antigo (versão 1), trocada pelas câmeras + corte. */
    private static final String ROTULO_TRANSMISSAO_ANTIGO = "Transmissão";

    private final EventoRepository eventoRepository;
    private final SlotEscalaRepository slotEscalaRepository;
    private final CalendarioService calendarioService;
    private final CandidaturaEscalaRepository candidaturaRepository;
    private final PublicacaoEscalaRepository publicacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlteracaoEscalaRepository historicoRepository;
    private final AvisoService avisoService;

    public EventoService(EventoRepository eventoRepository, SlotEscalaRepository slotEscalaRepository,
                         CalendarioService calendarioService, CandidaturaEscalaRepository candidaturaRepository,
                         PublicacaoEscalaRepository publicacaoRepository, UsuarioRepository usuarioRepository,
                         AlteracaoEscalaRepository historicoRepository, AvisoService avisoService) {
        this.eventoRepository = eventoRepository;
        this.slotEscalaRepository = slotEscalaRepository;
        this.calendarioService = calendarioService;
        this.candidaturaRepository = candidaturaRepository;
        this.publicacaoRepository = publicacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.historicoRepository = historicoRepository;
        this.avisoService = avisoService;
    }

    /** @param viewer quem está vendo (define o que fica oculto); null = ninguém em especial, tudo em montagem fica oculto */
    public List<EventoDTO> listarTodos(Usuario viewer) {
        List<Evento> eventos = eventoRepository.findByExcluidoEmIsNull();
        Visao v = visao(viewer, eventos);
        return eventos.stream().map(e -> toDTO(e, v)).toList();
    }

    public List<EventoDTO> listarLixeira(Usuario viewer) {
        List<Evento> eventos = eventoRepository.findByExcluidoEmIsNotNullOrderByExcluidoEmDesc();
        Visao v = visao(viewer, eventos);
        return eventos.stream().map(e -> toDTO(e, v)).toList();
    }

    /**
     * Qualquer coordenador (de setor ou geral) pode marcar um evento extra que precisa de escala.
     * Coordenador de setor só cria funções do próprio setor; os outros setores entram depois,
     * cada coordenação adicionando as suas funções pelo {@link #adicionarSlot}.
     */
    @Transactional
    public EventoDTO criar(EventoRequestDTO dto, Usuario solicitante) {
        if (!solicitante.podeMontarEscalas()) {
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

        return toDTO(eventoRepository.save(evento), solicitante);
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

        exigirMesAberto(slot.getEvento().getData());

        // Antes de qualquer outra checagem, pra não revelar se a função (em montagem) já tem alguém.
        // Função de setor é por candidatura: a coordenação escala e publica. "Eu vou" direto só
        // nas funções gerais (sem setor).
        if (slot.getSetor() != null) {
            throw new IllegalStateException("Nessa função é por candidatura: toque em \"Quero servir\" e a coordenação monta a escala.");
        }

        if (slot.getVoluntario() != null) {
            throw new IllegalStateException("Esse horário já foi preenchido por outro voluntário.");
        }

        // Cada pessoa só pega vaga dos setores dela (o principal e os extras marcados no perfil);
        // função geral (sem setor) é aberta a todos. Coordenação Geral faz parte de todos.
        if (slot.getSetor() != null && !voluntario.fazParteDoSetor(slot.getSetor())) {
            throw new IllegalStateException("Essa vaga é de " + nomeSetor(slot.getSetor())
                    + ". Se você também serve nesse setor, adicione ele no seu perfil.");
        }

        boolean jaInscrito = evento.getSlots().stream()
                .anyMatch(s -> s.getVoluntario() != null && s.getVoluntario().getId().equals(voluntario.getId()));

        if (jaInscrito) {
            throw new IllegalStateException("Você já está escalado para este evento.");
        }

        if (slotEscalaRepository.ocuparSeLivre(slotId, voluntario) == 0) {
            throw new IllegalStateException("Alguém acabou de pegar essa vaga. Escolha outra.");
        }

        return toDTO(eventoRepository.findById(eventoId).orElseThrow(), voluntario);
    }

    /** Coordenador de setor só adiciona funções do próprio setor; Coordenador Geral, de qualquer setor. */
    @Transactional
    public EventoDTO adicionarSlot(Long eventoId, SlotEscalaRequestDTO dto, Usuario solicitante) {
        if (!solicitante.podeMontarEscalas()) {
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
        registrar(slot.getSetor(), evento.getData(), solicitante,
                nome(solicitante) + " adicionou a função " + slot.getRotulo() + " em " + descreverMissa(evento) + ".");

        return toDTO(eventoRepository.findById(eventoId).orElseThrow(), solicitante);
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
        String antigo = slot.getRotulo();
        slot.setRotulo(rotulo.trim());
        registrar(slot.getSetor(), slot.getEvento().getData(), solicitante,
                nome(solicitante) + " renomeou a função " + antigo + " para " + slot.getRotulo() + " em " + descreverMissa(slot.getEvento()) + ".");
        slotEscalaRepository.save(slot);
        return toDTO(eventoRepository.findById(eventoId).orElseThrow(), solicitante);
    }

    /** Remove uma função da escala (mesma regra de permissão do {@link #renomearSlot}). */
    @Transactional
    public EventoDTO removerSlot(Long eventoId, Long slotId, Usuario solicitante) {
        SlotEscala slot = slotDoEvento(eventoId, slotId);
        exigirPodeEditarFuncao(slot, solicitante);
        candidaturaRepository.apagarDoSlot(slot);
        registrar(slot.getSetor(), slot.getEvento().getData(), solicitante,
                nome(solicitante) + " removeu a função " + slot.getRotulo() + " de " + descreverMissa(slot.getEvento())
                        + (slot.getVoluntario() != null ? " (estava " + slot.getVoluntario().getNome() + ")" : "") + ".");
        if (slot.getVoluntario() != null && funcaoPublicada(slot)) {
            avisoService.avisar(slot.getVoluntario(), "A função " + slot.getRotulo() + " em " + descreverMissa(slot.getEvento())
                    + " foi removida da escala, e você não está mais nela.");
        }
        Evento evento = slot.getEvento();
        evento.getSlots().remove(slot);
        eventoRepository.save(evento);
        return toDTO(eventoRepository.findById(eventoId).orElseThrow(), solicitante);
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
        if (solicitante.gereTodasAsEscalas()) return;
        if (solicitante.getRole() != Role.COORDENADOR) {
            throw new IllegalStateException("Apenas coordenadores podem alterar as funções da escala.");
        }
        if (slot.getSetor() == null || slot.getSetor() != solicitante.getSetor()) {
            throw new IllegalStateException("Você só pode alterar as funções do seu setor.");
        }
    }

    /** "Quero servir": a pessoa se candidata a uma função do setor dela. */
    @Transactional
    public EventoDTO candidatar(Long eventoId, Long slotId, Usuario voluntario) {
        SlotEscala slot = slotDoEvento(eventoId, slotId);
        if (slot.getSetor() == null) {
            throw new IllegalStateException("Essa é uma função geral: toque em \"Eu vou\".");
        }
        if (!voluntario.fazParteDoSetor(slot.getSetor())) {
            throw new IllegalStateException("Essa função é de " + nomeSetor(slot.getSetor())
                    + ". Se você também serve nesse setor, adicione ele no seu perfil.");
        }
        exigirMesAberto(slot.getEvento().getData());
        Set<String> publicados = publicacaoRepository.findByMesAndSetor(
                YearMonth.from(slot.getEvento().getData()).toString(), slot.getSetor()).isPresent()
                ? Set.of(chavePublicacao(slot.getEvento().getData(), slot.getSetor())) : Set.of();
        if (publicada(slot, publicados) && slot.getVoluntario() != null
                && !slot.getVoluntario().getId().equals(voluntario.getId())) {
            throw new IllegalStateException("Essa função já está preenchida nesta escala.");
        }
        if (candidaturaRepository.findBySlotIdAndUsuarioId(slotId, voluntario.getId()).isEmpty()) {
            candidaturaRepository.save(CandidaturaEscala.builder()
                    .slot(slot).usuario(voluntario).criadaEm(LocalDateTime.now()).build());
        }
        return toDTO(eventoRepository.findById(eventoId).orElseThrow(), voluntario);
    }

    @Transactional
    public EventoDTO desistirCandidatura(Long eventoId, Long slotId, Usuario voluntario) {
        SlotEscala slot = slotDoEvento(eventoId, slotId);
        exigirMesAberto(slot.getEvento().getData());
        candidaturaRepository.findBySlotIdAndUsuarioId(slotId, voluntario.getId())
                .ifPresent(candidaturaRepository::delete);
        return toDTO(eventoRepository.findById(eventoId).orElseThrow(), voluntario);
    }

    /**
     * A coordenação põe (ou troca) a pessoa numa função. Só quem coordena o setor da função (ou a
     * Coordenação Geral); a pessoa tem que fazer parte do setor e não pode estar em outra função
     * do mesmo evento.
     */
    @Transactional
    public EventoDTO escalar(Long eventoId, Long slotId, Long usuarioId, Usuario solicitante) {
        SlotEscala slot = slotDoEvento(eventoId, slotId);
        if (!coordenaFuncao(slot, solicitante)) {
            throw new IllegalStateException("Só a coordenação do setor pode montar a escala dessa função.");
        }
        Usuario pessoa = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Pessoa não encontrada."));
        if (slot.getSetor() != null && !pessoa.fazParteDoSetor(slot.getSetor())) {
            throw new IllegalStateException(pessoa.getNome() + " não faz parte de " + nomeSetor(slot.getSetor()) + ".");
        }
        slot.getEvento().getSlots().stream()
                .filter(o -> !o.getId().equals(slot.getId()) && o.getVoluntario() != null
                        && o.getVoluntario().getId().equals(pessoa.getId()))
                .findFirst()
                .ifPresent(o -> {
                    throw new IllegalStateException(pessoa.getNome() + " já está em \"" + o.getRotulo() + "\" nesta missa.");
                });
        Usuario anterior = slot.getVoluntario();
        slot.setVoluntario(pessoa);
        String missa = descreverMissa(slot.getEvento());
        registrar(slot.getSetor(), slot.getEvento().getData(), solicitante, anterior == null
                ? nome(solicitante) + " escalou " + pessoa.getNome() + " em " + slot.getRotulo() + ", " + missa + "."
                : nome(solicitante) + " trocou " + anterior.getNome() + " por " + pessoa.getNome() + " em " + slot.getRotulo() + ", " + missa + ".");
        // Em montagem ninguém é avisado (é sigiloso); a pessoa fica sabendo quando a escala for publicada.
        if (funcaoPublicada(slot) && (anterior == null || !anterior.getId().equals(pessoa.getId()))) {
            avisoService.avisar(pessoa, "Você foi escalado(a) em " + slot.getRotulo() + " na " + missa + ".");
            if (anterior != null) {
                avisoService.avisar(anterior, "A coordenação mudou a escala: você não está mais em " + slot.getRotulo()
                        + " na " + missa + ".");
            }
        }
        slotEscalaRepository.save(slot);
        return toDTO(eventoRepository.findById(eventoId).orElseThrow(), solicitante);
    }

    /** Publica a escala de um setor num mês: a partir daí a equipe vê quem foi escalado. */
    @Transactional
    public void publicar(YearMonth mes, Setor setor, Usuario solicitante) {
        exigirCoordenaSetor(setor, solicitante);
        if (publicacaoRepository.findByMesAndSetor(mes.toString(), setor).isEmpty()) {
            publicacaoRepository.save(PublicacaoEscala.builder()
                    .mes(mes.toString()).setor(setor).publicadaEm(LocalDateTime.now()).publicadaPor(solicitante).build());
            registrar(setor, mes.atDay(1), solicitante, nome(solicitante) + " publicou a escala de " + nomeMesExtenso(mes.atDay(1)) + ".");
            avisarPublicacao(mes, setor);
        }
    }

    /** Volta a escala pra "em montagem" (ex.: publicou cedo demais). */
    @Transactional
    public void despublicar(YearMonth mes, Setor setor, Usuario solicitante) {
        exigirCoordenaSetor(setor, solicitante);
        if (mesFechado(mes.atDay(1))) {
            throw new IllegalStateException("A escala desse mês já está fechada e não pode voltar pra montagem.");
        }
        publicacaoRepository.findByMesAndSetor(mes.toString(), setor).ifPresent(p -> {
            publicacaoRepository.delete(p);
            registrar(setor, mes.atDay(1), solicitante, nome(solicitante) + " voltou a escala de "
                    + nomeMesExtenso(mes.atDay(1)) + " pra montagem.");
        });
    }

    private void exigirCoordenaSetor(Setor setor, Usuario solicitante) {
        if (setor == null) throw new IllegalArgumentException("Informe o setor.");
        if (!solicitante.coordenaEscalaDoSetor(setor)) {
            throw new IllegalStateException("Só a coordenação de " + nomeSetor(setor) + " pode publicar essa escala.");
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
        boolean coordenaEquipeDela = solicitante.gereTodasAsEscalas()
                || (solicitante.getRole() == Role.COORDENADOR && escalado.fazParteDoSetor(solicitante.getSetor()));

        if (!ehElaMesma && !coordenaEquipeDela) {
            throw new IllegalStateException(solicitante.getRole() == Role.COORDENADOR
                    ? "Você só pode tirar da escala pessoas da sua equipe."
                    : "Apenas coordenadores podem tirar outra pessoa da escala.");
        }
        // Mês que já chegou tem a escala fechada: quem precisar sair fala com a coordenação,
        // que continua podendo trocar/tirar pra cobrir imprevistos.
        if (!coordenaEquipeDela && mesFechado(slot.getEvento().getData())) {
            throw new IllegalStateException("A escala de " + nomeMesExtenso(slot.getEvento().getData())
                    + " já está fechada. Pra sair, fale com a coordenação do seu setor.");
        }

        slot.setVoluntario(null);
        slotEscalaRepository.save(slot);
        String missa = descreverMissa(slot.getEvento());
        registrar(slot.getSetor(), slot.getEvento().getData(), solicitante, ehElaMesma
                ? escalado.getNome() + " saiu de " + slot.getRotulo() + ", " + missa + "."
                : nome(solicitante) + " tirou " + escalado.getNome() + " de " + slot.getRotulo() + ", " + missa + ".");
        if (!ehElaMesma && funcaoPublicada(slot)) {
            avisoService.avisar(escalado, "A coordenação tirou você da escala de " + slot.getRotulo() + " na " + missa + ".");
        }

        return toDTO(eventoRepository.findById(eventoId).orElseThrow(), solicitante);
    }

    // ---------- histórico e avisos ----------

    /** Histórico de alterações de um setor (mais recentes primeiro). Só coordenação do setor ou Geral. */
    public List<Map<String, Object>> historico(Setor setor, Usuario solicitante) {
        if (setor == null || !solicitante.coordenaEscalaDoSetor(setor)) {
            throw new IllegalStateException("Só a coordenação do setor pode ver o histórico da escala.");
        }
        return historicoRepository.findTop150BySetorOrderByCriadoEmDesc(setor).stream()
                .map(h -> Map.<String, Object>of("mes", h.getMes(), "texto", h.getTexto(), "criadoEm", h.getCriadoEm().toString()))
                .toList();
    }

    /** Só registra o que é de algum setor (funções gerais ficam de fora). */
    private void registrar(Setor setor, LocalDate dataEvento, Usuario autor, String texto) {
        if (setor == null) return;
        historicoRepository.save(AlteracaoEscala.builder()
                .setor(setor)
                .mes(YearMonth.from(dataEvento).toString())
                .texto(texto.length() > 500 ? texto.substring(0, 497) + "..." : texto)
                .autor(autor)
                .criadoEm(LocalDateTime.now())
                .build());
    }

    /** Ao publicar, cada pessoa escalada naquele setor/mês recebe um aviso com as funções dela. */
    private void avisarPublicacao(YearMonth mes, Setor setor) {
        List<Evento> eventos = eventoRepository.findByDataBetweenAndExcluidoEmIsNullOrderByDataAscHorarioAsc(
                mes.atDay(1), mes.atEndOfMonth());
        Map<Long, List<String>> porPessoa = new java.util.LinkedHashMap<>();
        Map<Long, Usuario> pessoas = new java.util.HashMap<>();
        for (Evento e : eventos) {
            for (SlotEscala sl : e.getSlots()) {
                if (sl.getSetor() == setor && sl.getVoluntario() != null) {
                    pessoas.put(sl.getVoluntario().getId(), sl.getVoluntario());
                    porPessoa.computeIfAbsent(sl.getVoluntario().getId(), k -> new ArrayList<>())
                            .add(sl.getRotulo() + " (" + descreverMissa(e) + ")");
                }
            }
        }
        porPessoa.forEach((id, funcoes) -> avisoService.avisar(pessoas.get(id),
                "Saiu a escala de " + nomeMesExtenso(mes.atDay(1)) + " de " + nomeSetor(setor) + "! Você está em: "
                        + String.join("; ", funcoes) + "."));
    }

    private boolean funcaoPublicada(SlotEscala s) {
        return s.getSetor() == null || publicacaoRepository.findByMesAndSetor(
                YearMonth.from(s.getEvento().getData()).toString(), s.getSetor()).isPresent();
    }

    /** "Missa das 10h de domingo, 22/11" */
    private static String descreverMissa(Evento e) {
        String dia = e.getData().getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("pt-BR"));
        return e.getTitulo() + " de " + dia + ", " + e.getData().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
    }

    private static String nome(Usuario u) {
        return u == null ? "O sistema" : u.getNome();
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
        try {
            publicarMesQueChegou();
        } catch (Exception ignorada) {
            // idem: roda de novo na próxima subida da aplicação
        }
    }

    /**
     * O mês que chegou não pode ficar "em montagem": no dia 1 (e na subida da aplicação, caso o
     * servidor estivesse fora do ar no dia 1), publica a escala do mês atual de todo setor que a
     * coordenação não publicou. A partir do dia 25 do mês anterior o front já avisa a coordenação.
     *
     * @return quantos setores foram publicados agora
     */
    @Transactional
    public int publicarMesQueChegou() {
        String mes = YearMonth.from(calendarioService.hoje()).toString();
        int publicados = 0;
        for (Setor setor : Setor.values()) {
            if (publicacaoRepository.findByMesAndSetor(mes, setor).isEmpty()) {
                publicacaoRepository.save(PublicacaoEscala.builder()
                        .mes(mes).setor(setor).publicadaEm(LocalDateTime.now()).build());
                publicados++;
                LocalDate primeiro = YearMonth.parse(mes).atDay(1);
                registrar(setor, primeiro, null, "Escala de " + nomeMesExtenso(primeiro)
                        + " publicada automaticamente no dia 1º (ninguém tinha publicado).");
                avisarPublicacao(YearMonth.parse(mes), setor);
            }
        }
        return publicados;
    }

    /**
     * Primeiro mês em que o fechamento vale. O sistema começou a ser usado no meio de outubro de
     * 2026, então a escala de outubro fica aberta até o fim; de novembro em diante, todo mês fecha
     * quando chega.
     */
    static final YearMonth PRIMEIRO_MES_COM_FECHAMENTO = YearMonth.of(2026, 11);

    /** Mês que já chegou (ou passou): a escala está fechada pra equipe. */
    private boolean mesFechado(LocalDate data) {
        YearMonth m = YearMonth.from(data);
        return !m.isBefore(PRIMEIRO_MES_COM_FECHAMENTO) && !m.isAfter(YearMonth.from(calendarioService.hoje()));
    }

    private void exigirMesAberto(LocalDate data) {
        if (mesFechado(data)) {
            throw new IllegalStateException("A escala de " + nomeMesExtenso(data)
                    + " já está fechada. Se precisar mudar algo, fale com a coordenação do seu setor.");
        }
    }

    private static String nomeMesExtenso(LocalDate data) {
        String m = data.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("pt-BR"));
        return m.substring(0, 1).toUpperCase() + m.substring(1);
    }

    /** Escala completa do mês: todos os eventos com escala, com as funções agrupadas por setor. */
    @Transactional(readOnly = true)
    public EscalaCompletaDTO escalaCompleta(YearMonth mes, Usuario viewer) {
        return escalaCompleta(mes, null, viewer);
    }

    /**
     * Escala do mês. Com {@code setor} preenchido, vira a escala só daquele setor: cada evento
     * mostra as funções do setor e as funções gerais (as mesmas que aparecem na aba "Escala de
     * <setor>" do front), e eventos sem nenhuma dessas funções ficam de fora.
     */
    public EscalaCompletaDTO escalaCompleta(YearMonth mes, Setor setor, Usuario viewer) {
        List<Evento> eventos = eventoRepository.findByDataBetweenAndExcluidoEmIsNullOrderByDataAscHorarioAsc(
                mes.atDay(1), mes.atEndOfMonth());

        Visao v = visao(viewer, eventos);
        List<Setor> ordem = new ArrayList<>();
        if (setor == null) {
            ordem.addAll(List.of(Setor.values()));
        } else {
            ordem.add(setor);
        }
        // Funções gerais (sem setor) sempre por último.
        ordem.add(null);

        List<EscalaCompletaDTO.EventoEscala> itens = eventos.stream()
                .map(e -> toEventoEscala(e, ordem, v))
                .filter(e -> !e.setores().isEmpty())
                .toList();

        return new EscalaCompletaDTO(mes.toString(), setor, setor == null ? null : nomeSetor(setor),
                calendarioService.domingosDoMes(mes), itens);
    }

    private EscalaCompletaDTO.EventoEscala toEventoEscala(Evento e, List<Setor> ordem, Visao v) {
        List<EscalaCompletaDTO.GrupoSetor> grupos = new ArrayList<>();
        int vagas = 0;
        for (Setor setor : ordem) {
            List<SlotEscalaDTO> funcoes = e.getSlots().stream()
                    .filter(s -> Objects.equals(s.getSetor(), setor))
                    .sorted(Comparator.comparing(SlotEscala::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(sl -> toSlotDTO(sl, v))
                    .toList();
            if (!funcoes.isEmpty()) {
                grupos.add(new EscalaCompletaDTO.GrupoSetor(setor, nomeSetor(setor), funcoes));
                // Função oculta (escala em montagem) não conta como vaga aberta pra quem está vendo.
                vagas += (int) funcoes.stream().filter(f -> f.voluntarioId() == null && !f.oculto()).count();
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
        if (solicitante.gereTodasAsEscalas()) return pedido;
        return solicitante.getSetor();
    }

    @Transactional
    public EventoDTO moverParaLixeira(Long id, MotivoExclusao motivo, String detalhe, Usuario solicitante) {
        if (!solicitante.gereTodasAsEscalas()) {
            throw new IllegalStateException("Apenas a Coordenação Geral ou quem gere as escalas pode excluir eventos da escala.");
        }
        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado."));
        evento.setExcluidoEm(LocalDateTime.now());
        evento.setMotivoExclusao(motivo);
        evento.setDetalheExclusao(detalhe);
        return toDTO(eventoRepository.save(evento), solicitante);
    }

    @Transactional
    public EventoDTO restaurar(Long id, Usuario solicitante) {
        if (!solicitante.gereTodasAsEscalas()) {
            throw new IllegalStateException("Apenas a Coordenação Geral ou quem gere as escalas pode restaurar eventos da lixeira.");
        }
        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado."));
        if (evento.getExcluidoEm() == null) {
            throw new IllegalStateException("Este evento não está na lixeira.");
        }
        evento.setExcluidoEm(null);
        evento.setMotivoExclusao(null);
        evento.setDetalheExclusao(null);
        return toDTO(eventoRepository.save(evento), solicitante);
    }

    /**
     * O que cada pessoa pode ver da escala, calculado uma vez por resposta: quais (mês, setor) já
     * foram publicados e as candidaturas das funções envolvidas.
     */
    private record Visao(Usuario viewer, Set<String> publicados, Map<Long, List<CandidaturaEscala>> candidaturas) {}

    private Visao visao(Usuario viewer, Collection<Evento> eventos) {
        Set<String> publicados = publicacaoRepository.findAll().stream()
                .map(p -> p.getMes() + "|" + p.getSetor().name())
                .collect(Collectors.toCollection(HashSet::new));
        List<Long> slotIds = eventos.stream().flatMap(e -> e.getSlots().stream()).map(SlotEscala::getId)
                .filter(Objects::nonNull).toList();
        Map<Long, List<CandidaturaEscala>> cands = slotIds.isEmpty() ? Map.of()
                : candidaturaRepository.findBySlotIdIn(slotIds).stream()
                    .collect(Collectors.groupingBy(c -> c.getSlot().getId()));
        return new Visao(viewer, publicados, cands);
    }

    private static String chavePublicacao(LocalDate data, Setor setor) {
        return YearMonth.from(data) + "|" + setor.name();
    }

    /** Função geral (sem setor) não passa por montagem/publicação. */
    private static boolean publicada(SlotEscala s, Set<String> publicados) {
        return s.getSetor() == null || publicados.contains(chavePublicacao(s.getEvento().getData(), s.getSetor()));
    }

    /** Quem monta a escala da função: coordenação do setor ou Coordenação Geral (função geral: só a Geral). */
    private static boolean coordenaFuncao(SlotEscala s, Usuario u) {
        if (u == null) return false;
        return s.getSetor() == null ? u.gereTodasAsEscalas() : u.coordenaEscalaDoSetor(s.getSetor());
    }

    private SlotEscalaDTO toSlotDTO(SlotEscala s, Visao v) {
        boolean publicada = publicada(s, v.publicados());
        boolean coord = coordenaFuncao(s, v.viewer());
        boolean oculto = !publicada && !coord;
        List<CandidaturaEscala> cands = v.candidaturas().getOrDefault(s.getId(), List.of());
        boolean euMe = v.viewer() != null && cands.stream().anyMatch(c -> c.getUsuario().getId().equals(v.viewer().getId()));
        List<UsuarioResumoDTO> candidatos = coord
                ? cands.stream().map(c -> UsuarioResponseDTO.resumo(c.getUsuario())).toList()
                : null;
        Usuario quem = oculto ? null : s.getVoluntario();
        return new SlotEscalaDTO(
                s.getId(),
                s.getRotulo(),
                s.getTipo(),
                quem != null ? quem.getId() : null,
                quem != null ? quem.getNome() : null,
                s.getSetor(),
                publicada,
                oculto,
                euMe,
                candidatos
        );
    }

    private EventoDTO toDTO(Evento e, Usuario viewer) {
        return toDTO(e, visao(viewer, List.of(e)));
    }

    private EventoDTO toDTO(Evento e, Visao v) {
        List<SlotEscalaDTO> slotsDto = e.getSlots().stream().map(sl -> toSlotDTO(sl, v)).toList();

        return new EventoDTO(
                e.getId(), e.getTitulo(), e.getData(), e.getHorario(),
                e.getLocal(), e.getEventoGrande(), slotsDto,
                e.getExcluidoEm(), e.getMotivoExclusao(), e.getDetalheExclusao()
        );
    }
}
