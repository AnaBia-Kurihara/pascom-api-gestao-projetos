package br.org.pascom.service;

import br.org.pascom.dto.CartaoRequestDTO;
import br.org.pascom.dto.CartaoResponseDTO;
import br.org.pascom.dto.IdeiaRequestDTO;
import br.org.pascom.dto.IdeiaResponseDTO;
import br.org.pascom.model.Ideia;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Formato;
import br.org.pascom.model.enums.MotivoExclusao;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.repository.IdeiaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class IdeiaService {

    private final IdeiaRepository ideiaRepository;
    private final CartaoService cartaoService;

    public IdeiaService(IdeiaRepository ideiaRepository, CartaoService cartaoService) {
        this.ideiaRepository = ideiaRepository;
        this.cartaoService = cartaoService;
    }

    public List<IdeiaResponseDTO> listarTodas() {
        return ideiaRepository.findByExcluidoEmIsNull().stream().map(IdeiaResponseDTO::from).toList();
    }

    public List<IdeiaResponseDTO> listarPorSetor(Setor setor) {
        return ideiaRepository.findBySetorAndExcluidoEmIsNull(setor).stream().map(IdeiaResponseDTO::from).toList();
    }

    public List<IdeiaResponseDTO> listarLixeira(Setor setor) {
        return ideiaRepository.findBySetorAndExcluidoEmIsNotNullOrderByExcluidoEmDesc(setor).stream()
                .map(IdeiaResponseDTO::from).toList();
    }

    @Transactional
    public IdeiaResponseDTO criar(IdeiaRequestDTO dto, Usuario autor) {
        Ideia ideia = Ideia.builder()
                .titulo(dto.titulo())
                .descricao(dto.descricao())
                .tema(dto.tema())
                .setor(dto.setor())
                .autor(autor)
                .adotada(false)
                .build();

        ideia.getVotantesIds().add(autor.getId()); // O próprio autor já vota automaticamente
        return IdeiaResponseDTO.from(ideiaRepository.save(ideia));
    }

    @Transactional
    public IdeiaResponseDTO alternarVoto(Long ideiaId, Usuario usuario) {
        Ideia ideia = ideiaRepository.findById(ideiaId)
                .orElseThrow(() -> new IllegalArgumentException("Ideia não encontrada."));

        Long usuarioId = usuario.getId();
        if (ideia.getVotantesIds().contains(usuarioId)) {
            ideia.getVotantesIds().remove(usuarioId);
        } else {
            ideia.getVotantesIds().add(usuarioId);
        }

        return IdeiaResponseDTO.from(ideiaRepository.save(ideia));
    }

    @Transactional
    public CartaoResponseDTO transformarEmCartao(Long ideiaId) {
        Ideia ideia = ideiaRepository.findById(ideiaId)
                .orElseThrow(() -> new IllegalArgumentException("Ideia não encontrada."));

        if (Boolean.TRUE.equals(ideia.getAdotada())) {
            throw new IllegalStateException("Esta ideia já foi adotada e virou um cartão.");
        }

        ideia.setAdotada(true);
        ideiaRepository.save(ideia);

        CartaoRequestDTO cartaoReq = new CartaoRequestDTO(
                ideia.getTitulo(),
                Formato.REELS,
                ideia.getTema(),
                null,
                null,
                null,
                ideia.getDescricao(),
                null,
                ideia.getSetor()
        );

        return cartaoService.criar(cartaoReq);
    }

    @Transactional
    public IdeiaResponseDTO moverParaLixeira(Long id, MotivoExclusao motivo, String detalhe) {
        Ideia ideia = ideiaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ideia não encontrada."));
        ideia.setExcluidoEm(LocalDateTime.now());
        ideia.setMotivoExclusao(motivo);
        ideia.setDetalheExclusao(detalhe);
        return IdeiaResponseDTO.from(ideiaRepository.save(ideia));
    }

    @Transactional
    public IdeiaResponseDTO restaurar(Long id) {
        Ideia ideia = ideiaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ideia não encontrada."));
        if (ideia.getExcluidoEm() == null) {
            throw new IllegalStateException("Esta ideia não está na lixeira.");
        }
        ideia.setExcluidoEm(null);
        ideia.setMotivoExclusao(null);
        ideia.setDetalheExclusao(null);
        return IdeiaResponseDTO.from(ideiaRepository.save(ideia));
    }
}