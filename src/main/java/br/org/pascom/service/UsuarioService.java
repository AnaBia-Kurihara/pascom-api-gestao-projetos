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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final EmailService emailService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager, TokenService tokenService,
                           EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.emailService = emailService;
    }

    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll().stream().map(UsuarioResponseDTO::from).toList();
    }

    @Transactional
    public UsuarioResponseDTO cadastrar(UsuarioCadastroDTO dados) {
        if (usuarioRepository.findByEmail(dados.email()).isPresent()) {
            throw new IllegalStateException("Já existe uma conta com este e-mail.");
        }

        if (dados.role() == Role.COORDENADOR_GERAL && usuarioRepository.existsByRole(Role.COORDENADOR_GERAL)) {
            throw new IllegalStateException("Já existe um Coordenador Geral cadastrado. Fale com essa pessoa pra acessar o sistema.");
        }

        if (dados.role() != Role.COORDENADOR_GERAL && dados.setor() == null) {
            throw new IllegalArgumentException("Selecione o setor.");
        }

        Usuario usuario = Usuario.builder()
                .nome(dados.nome())
                .email(dados.email())
                .senha(passwordEncoder.encode(dados.senha()))
                .funcao(dados.funcao())
                .disponibilidade(dados.disponibilidade())
                .avatarHue(dados.avatarHue())
                .role(dados.role())
                // Coordenador geral não pertence a um setor específico, coordena todos.
                .setor(dados.role() == Role.COORDENADOR_GERAL ? null : dados.setor())
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
        if (solicitante.getRole() != Role.COORDENADOR_GERAL) {
            throw new IllegalStateException("Só o Coordenador Geral pode alterar a função de alguém da equipe.");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        usuario.setRole(novoRole);
        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public void esqueciSenha(String email) {
        // Não revela se o e-mail existe ou não — sempre "sucesso" do ponto de vista de quem pediu.
        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            usuario.setResetSenhaToken(UUID.randomUUID().toString());
            usuario.setResetSenhaExpiraEm(LocalDateTime.now().plusHours(1));
            usuarioRepository.save(usuario);
            emailService.enviarLinkRedefinicaoSenha(usuario.getEmail(), usuario.getResetSenhaToken());
        });
    }

    @Transactional
    public void redefinirSenha(String token, String novaSenha) {
        Usuario usuario = usuarioRepository.findByResetSenhaToken(token)
                .orElseThrow(() -> new IllegalStateException("Link de redefinição inválido ou já usado."));

        if (usuario.getResetSenhaExpiraEm() == null || usuario.getResetSenhaExpiraEm().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Esse link expirou. Peça um novo e-mail de redefinição.");
        }

        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuario.setResetSenhaToken(null);
        usuario.setResetSenhaExpiraEm(null);
        usuarioRepository.save(usuario);
    }
}