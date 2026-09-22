package br.org.pascom.service;

import br.org.pascom.dto.CartaoRequestDTO;
import br.org.pascom.dto.CartaoResponseDTO;
import br.org.pascom.dto.IdeiaRequestDTO;
import br.org.pascom.dto.IdeiaResponseDTO;
import br.org.pascom.model.Ideia;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Formato;
import br.org.pascom.repository.IdeiaRepository;
import br.org.pascom.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IdeiaService {

    private final IdeiaRepository ideiaRepository;
    private final UsuarioRepository usuarioRepository;
    private final CartaoService cartaoService;

    public IdeiaService(IdeiaRepository ideiaRepository, UsuarioRepository usuarioRepository, CartaoService cartaoService) {
        this.ideiaRepository = ideiaRepository;
        this.usuarioRepository = usuarioRepository;
        this.cartaoService = cartaoService;
    }

    public List<IdeiaResponseDTO> listarTodas() {
        return ideiaRepository.findAll().stream().map(IdeiaResponseDTO::from).toList();
    }

    @Transactional
    public IdeiaResponseDTO criar(IdeiaRequestDTO dto, Long autorId) {
        Usuario autor = usuarioRepository.findById(autorId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário autor não encontrado."));

        Ideia ideia = Ideia.builder()
                .titulo(dto.titulo())
                .descricao(dto.descricao())
                .tema(dto.tema())
                .autor(autor)
                .adotada(false)
                .build();

        ideia.getVotantesIds().add(autorId); // O próprio autor já vota automaticamente
        return IdeiaResponseDTO.from(ideiaRepository.save(ideia));
    }

    @Transactional
    public IdeiaResponseDTO alternarVoto(Long ideiaId, Long usuarioId) {
        Ideia ideia = ideiaRepository.findById(ideiaId)
                .orElseThrow(() -> new IllegalArgumentException("Ideia não encontrada."));

        if (!usuarioRepository.existsById(usuarioId)) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }

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
                null
        );

        return cartaoService.criar(cartaoReq);
    }
}