package br.org.pascom.service;

import br.org.pascom.dto.TransmissaoRequestDTO;
import br.org.pascom.dto.TransmissaoResponseDTO;
import br.org.pascom.model.Transmissao;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.EtapaTransmissao;
import br.org.pascom.model.enums.TipoTransmissao;
import br.org.pascom.repository.TransmissaoRepository;
import br.org.pascom.repository.UsuarioRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class TransmissaoService {

    private final TransmissaoRepository transmissaoRepository;
    private final UsuarioRepository usuarioRepository;

    public TransmissaoService(TransmissaoRepository transmissaoRepository, UsuarioRepository usuarioRepository) {
        this.transmissaoRepository = transmissaoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<TransmissaoResponseDTO> listarTodas() {
        return transmissaoRepository.findAllByOrderByDataAscHorarioAsc().stream()
                .map(TransmissaoResponseDTO::from).toList();
    }

    @Transactional
    public TransmissaoResponseDTO criar(TransmissaoRequestDTO dados) {
        Transmissao t = new Transmissao();
        t.setEtapa(EtapaTransmissao.PLANEJANDO);
        preencherCamposEditaveis(t, dados);
        return TransmissaoResponseDTO.from(transmissaoRepository.save(t));
    }

    @Transactional
    public TransmissaoResponseDTO atualizar(Long id, TransmissaoRequestDTO dados) {
        Transmissao t = buscarPorId(id);
        preencherCamposEditaveis(t, dados);
        return TransmissaoResponseDTO.from(transmissaoRepository.save(t));
    }

    @Transactional
    public TransmissaoResponseDTO moverEtapa(Long id, EtapaTransmissao novaEtapa) {
        Transmissao t = buscarPorId(id);
        t.setEtapa(novaEtapa);
        return TransmissaoResponseDTO.from(transmissaoRepository.save(t));
    }

    @Transactional
    public void deletar(Long id) {
        transmissaoRepository.delete(buscarPorId(id));
    }

    /**
     * Gera automaticamente a transmissão da "Missa das 10h" pra cada domingo do mês
     * informado — sem duplicar se já existir uma transmissão com essa data+título.
     */
    @Transactional
    public int gerarTransmissoesDominicaisDoMes(YearMonth mes) {
        int criados = 0;
        LocalDate primeiroDomingo = mes.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        for (LocalDate domingo = primeiroDomingo; !domingo.isAfter(mes.atEndOfMonth()); domingo = domingo.plusWeeks(1)) {
            if (transmissaoRepository.existsByDataAndTitulo(domingo, "Missa das 10h")) {
                continue;
            }
            Transmissao t = Transmissao.builder()
                    .data(domingo)
                    .horario(LocalTime.of(10, 0))
                    .titulo("Missa das 10h")
                    .tipo(TipoTransmissao.MISSA_DOMINICAL)
                    .etapa(EtapaTransmissao.PLANEJANDO)
                    .build();
            transmissaoRepository.save(t);
            criados++;
        }
        return criados;
    }

    /** Roda no dia 1 de cada mês às 6h: garante que os domingos do mês já têm transmissão planejada. */
    @Scheduled(cron = "0 0 6 1 * *")
    public void gerarTransmissoesDoMesAgendado() {
        try {
            gerarTransmissoesDominicaisDoMes(YearMonth.now());
        } catch (Exception ignorada) {
            // não deve travar a aplicação; dá pra gerar manualmente depois
        }
    }

    private Transmissao buscarPorId(Long id) {
        return transmissaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transmissão não encontrada."));
    }

    private void preencherCamposEditaveis(Transmissao t, TransmissaoRequestDTO dados) {
        t.setData(dados.data());
        t.setHorario(dados.horario());
        t.setTitulo(dados.titulo());
        t.setTipo(dados.tipo());
        t.setTemaLiturgico(dados.temaLiturgico());
        t.setLink(dados.link());
        t.setCapaLink(dados.capaLink());
        t.setResponsavel(dados.responsavelId() != null
                ? usuarioRepository.findById(dados.responsavelId())
                        .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."))
                : null);
    }
}
