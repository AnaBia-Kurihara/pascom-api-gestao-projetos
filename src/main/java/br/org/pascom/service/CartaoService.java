package br.org.pascom.service;

import br.org.pascom.dto.CartaoRequestDTO;
import br.org.pascom.dto.CartaoResponseDTO;
import br.org.pascom.dto.ComentarioResponseDTO;
import br.org.pascom.dto.NovoComentarioDTO;
import br.org.pascom.model.Cartao;
import br.org.pascom.model.Checklist;
import br.org.pascom.model.Comentario;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Etapa;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.repository.CartaoRepository;
import br.org.pascom.repository.ComentarioRepository;
import br.org.pascom.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CartaoService {

    private final CartaoRepository cartaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ComentarioRepository comentarioRepository;

    public CartaoService(CartaoRepository cartaoRepository, UsuarioRepository usuarioRepository,
                          ComentarioRepository comentarioRepository) {
        this.cartaoRepository = cartaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.comentarioRepository = comentarioRepository;
    }

    public List<CartaoResponseDTO> listarTodos() {
        return cartaoRepository.findAll().stream().map(CartaoResponseDTO::from).toList();
    }

    public List<CartaoResponseDTO> listarPorSetor(Setor setor) {
        return cartaoRepository.findBySetor(setor).stream().map(CartaoResponseDTO::from).toList();
    }

    public Cartao buscarPorId(Long id) {
        return cartaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cartão não encontrado com o ID: " + id));
    }

    public CartaoResponseDTO buscarDtoPorId(Long id) {
        return CartaoResponseDTO.from(buscarPorId(id));
    }

    @Transactional
    public CartaoResponseDTO criar(CartaoRequestDTO dados) {
        Cartao cartao = new Cartao();
        preencherCamposEditaveis(cartao, dados);
        cartao.setEtapa(Etapa.IDEIA);
        cartao.setChecklist(dados.checklist() != null ? dados.checklist() : new Checklist());
        return CartaoResponseDTO.from(cartaoRepository.save(cartao));
    }

    @Transactional
    public CartaoResponseDTO atualizar(Long id, CartaoRequestDTO dados) {
        Cartao existente = buscarPorId(id);
        preencherCamposEditaveis(existente, dados);
        if (dados.checklist() != null) {
            existente.setChecklist(dados.checklist());
        }
        return CartaoResponseDTO.from(cartaoRepository.save(existente));
    }

    private void preencherCamposEditaveis(Cartao cartao, CartaoRequestDTO dados) {
        cartao.setTitulo(dados.titulo());
        cartao.setFormato(dados.formato());
        cartao.setTema(dados.tema());
        cartao.setPrazoEntrega(dados.prazoEntrega());
        cartao.setDataPublicacao(dados.dataPublicacao());
        cartao.setRoteiroNotas(dados.roteiroNotas());
        cartao.setSetor(dados.setor());

        if (dados.responsavelId() != null) {
            Usuario responsavel = usuarioRepository.findById(dados.responsavelId())
                    .orElseThrow(() -> new IllegalArgumentException("Responsável não encontrado."));
            cartao.setResponsavel(responsavel);
        } else {
            cartao.setResponsavel(null);
        }
    }

    @Transactional
    public CartaoResponseDTO moverEtapa(Long cartaoId, Etapa novaEtapa) {
        Cartao cartao = buscarPorId(cartaoId);

        if (novaEtapa == Etapa.AGENDADO || novaEtapa == Etapa.PUBLICADO) {
            if (cartao.getChecklist() == null || !cartao.getChecklist().isCompleto()) {
                throw new IllegalStateException("Não é possível avançar. O checklist de segurança precisa estar 100% completo.");
            }
        }

        cartao.setEtapa(novaEtapa);
        return CartaoResponseDTO.from(cartaoRepository.save(cartao));
    }

    @Transactional
    public ComentarioResponseDTO adicionarComentario(Long cartaoId, Usuario autor, NovoComentarioDTO dados) {
        Cartao cartao = buscarPorId(cartaoId);

        Comentario comentario = Comentario.builder()
                .texto(dados.texto())
                .criadoEm(LocalDateTime.now())
                .autor(autor)
                .cartao(cartao)
                .build();

        // Salva o comentário diretamente (não via cascade do Cartao) para que o INSERT
        // aconteça na hora e o ID gerado (IDENTITY) já volte preenchido na resposta.
        comentarioRepository.save(comentario);
        return ComentarioResponseDTO.from(comentario);
    }

    @Transactional
    public void deletar(Long id) {
        Cartao cartao = buscarPorId(id);
        cartaoRepository.delete(cartao);
    }
}