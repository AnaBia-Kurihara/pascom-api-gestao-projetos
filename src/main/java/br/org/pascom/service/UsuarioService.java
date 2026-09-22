package br.org.pascom.service;

import br.org.pascom.dto.UsuarioCadastroDTO;
import br.org.pascom.dto.UsuarioResponseDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Role;
import br.org.pascom.repository.UsuarioRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll().stream().map(UsuarioResponseDTO::from).toList();
    }

    @Transactional
    public UsuarioResponseDTO cadastrar(UsuarioCadastroDTO dados) {
        if (usuarioRepository.findByEmail(dados.email()).isPresent()) {
            throw new IllegalStateException("Já existe uma conta com este e-mail.");
        }

        Usuario usuario = Usuario.builder()
                .nome(dados.nome())
                .email(dados.email())
                .senha(encoder.encode(dados.senha()))
                .funcao(dados.funcao())
                .disponibilidade(dados.disponibilidade())
                .avatarHue(dados.avatarHue())
                .role(usuarioRepository.count() == 0 ? Role.COORDENACAO : Role.VOLUNTARIO)
                .build();

        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    public UsuarioResponseDTO login(String email, String senha) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("E-mail ou senha inválidos."));

        if (!encoder.matches(senha, usuario.getSenha())) {
            throw new IllegalArgumentException("E-mail ou senha inválidos.");
        }

        return UsuarioResponseDTO.from(usuario);
    }

    @Transactional
    public UsuarioResponseDTO alterarRole(Long id, Role novoRole, Long solicitanteId) {
        Usuario solicitante = usuarioRepository.findById(solicitanteId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário solicitante não encontrado."));

        if (solicitante.getRole() != Role.COORDENACAO) {
            throw new IllegalStateException("Só a coordenação pode alterar a função de alguém da equipe.");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        usuario.setRole(novoRole);
        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }
}