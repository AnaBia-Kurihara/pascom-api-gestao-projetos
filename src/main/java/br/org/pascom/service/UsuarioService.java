package br.org.pascom.service;

import br.org.pascom.dto.LoginResponseDTO;
import br.org.pascom.dto.UsuarioCadastroDTO;
import br.org.pascom.dto.UsuarioResponseDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Role;
import br.org.pascom.repository.UsuarioRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager, TokenService tokenService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
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
                .senha(passwordEncoder.encode(dados.senha()))
                .funcao(dados.funcao())
                .disponibilidade(dados.disponibilidade())
                .avatarHue(dados.avatarHue())
                .role(usuarioRepository.count() == 0 ? Role.COORDENACAO : Role.VOLUNTARIO)
                .build();

        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    public LoginResponseDTO login(String email, String senha) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, senha));

        Usuario usuario = (Usuario) authentication.getPrincipal();
        String token = tokenService.gerarToken(usuario);
        return new LoginResponseDTO(token, UsuarioResponseDTO.from(usuario));
    }

    @Transactional
    public UsuarioResponseDTO alterarRole(Long id, Role novoRole, Usuario solicitante) {
        if (solicitante.getRole() != Role.COORDENACAO) {
            throw new IllegalStateException("Só a coordenação pode alterar a função de alguém da equipe.");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        usuario.setRole(novoRole);
        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }
}