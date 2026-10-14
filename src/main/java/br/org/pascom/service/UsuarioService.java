package br.org.pascom.service;

import br.org.pascom.dto.GoogleAuthDTO;
import br.org.pascom.dto.GoogleAuthResponseDTO;
import br.org.pascom.dto.LoginResponseDTO;
import br.org.pascom.dto.PerfilUpdateDTO;
import br.org.pascom.dto.UsuarioCadastroDTO;
import br.org.pascom.dto.UsuarioResponseDTO;
import br.org.pascom.model.FotoUsuario;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Role;
import br.org.pascom.model.enums.Setor;
import br.org.pascom.repository.FotoUsuarioRepository;
import br.org.pascom.repository.UsuarioRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final GoogleTokenService googleTokenService;
    private final FotoUsuarioRepository fotoUsuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager, TokenService tokenService,
                           EmailService emailService, GoogleTokenService googleTokenService,
                           FotoUsuarioRepository fotoUsuarioRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.googleTokenService = googleTokenService;
        this.fotoUsuarioRepository = fotoUsuarioRepository;
    }

    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll().stream().map(UsuarioResponseDTO::from).toList();
    }

    @Transactional
    public UsuarioResponseDTO cadastrar(UsuarioCadastroDTO dados) {
        String email = Usuario.normalizarEmail(dados.email());
        if (usuarioRepository.findByEmail(email).isPresent()) {
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
                .email(email)
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
                new UsernamePasswordAuthenticationToken(Usuario.normalizarEmail(email), senha));

        Usuario usuario = (Usuario) authentication.getPrincipal();
        String token = tokenService.gerarToken(usuario);
        return new LoginResponseDTO(token, UsuarioResponseDTO.from(usuario));
    }

    @Transactional
    public UsuarioResponseDTO alterarRole(Long id, Role novoRole, Usuario solicitante) {
        if (!solicitante.temPoderesDeCoordenadorGeral()) {
            throw new IllegalStateException("Só o Coordenador Geral pode alterar a função de alguém da equipe.");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        usuario.setRole(novoRole);
        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    /**
     * A própria pessoa atualiza nome, função, disponibilidade e os setores extras dos quais também
     * faz parte. Papel e setor principal não mudam por aqui (papel só o Coordenador Geral altera).
     */
    @Transactional
    public UsuarioResponseDTO atualizarPerfil(PerfilUpdateDTO dados, Usuario solicitante) {
        Usuario usuario = buscar(solicitante.getId());

        usuario.setNome(dados.nome().trim());
        usuario.setFuncao(textoOuNull(dados.funcao()));
        usuario.setDisponibilidade(textoOuNull(dados.disponibilidade()));

        EnumSet<Setor> extras = EnumSet.noneOf(Setor.class);
        // Coordenador Geral já faz parte de todos os setores; o principal não precisa repetir.
        if (dados.setoresExtras() != null && usuario.getRole() != Role.COORDENADOR_GERAL) {
            dados.setoresExtras().stream()
                    .filter(s -> s != null && s != usuario.getSetor())
                    .forEach(extras::add);
        }
        usuario.getSetoresExtras().clear();
        usuario.getSetoresExtras().addAll(extras);

        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    /** Troca a foto de perfil de quem está logado. Aceita só JPEG, PNG ou WebP de verdade (confere os bytes). */
    @Transactional
    public UsuarioResponseDTO salvarFoto(byte[] dados, Usuario solicitante) {
        if (dados == null || dados.length == 0) {
            throw new IllegalArgumentException("Envie uma imagem.");
        }
        if (dados.length > FotoUsuario.TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("A foto passou de 1 MB. Escolha uma imagem menor.");
        }
        String tipo = detectarTipoImagem(dados);
        if (tipo == null) {
            throw new IllegalArgumentException("Formato de imagem não suportado. Use JPG, PNG ou WebP.");
        }

        Usuario usuario = buscar(solicitante.getId());
        fotoUsuarioRepository.save(new FotoUsuario(usuario.getId(), dados, tipo));
        usuario.setFotoAtualizadaEm(LocalDateTime.now());
        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponseDTO removerFoto(Usuario solicitante) {
        Usuario usuario = buscar(solicitante.getId());
        fotoUsuarioRepository.deleteById(usuario.getId());
        usuario.setFotoAtualizadaEm(null);
        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    public Optional<FotoUsuario> buscarFoto(Long usuarioId) {
        return fotoUsuarioRepository.findById(usuarioId);
    }

    /** Identifica a imagem pelos primeiros bytes, sem confiar no Content-Type que o cliente mandou. */
    static String detectarTipoImagem(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
    }

    private static String textoOuNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Plano B da recuperação de senha, pra quando o e-mail não chega: a Coordenação Geral gera um
     * link de nova senha pra uma pessoa e manda por fora (WhatsApp). Mesmo token do "esqueci a
     * senha", mas vale 24h (dá tempo de a pessoa ver a mensagem).
     */
    @Transactional
    public String gerarLinkRedefinicao(Long usuarioId, Usuario solicitante) {
        if (!solicitante.temPoderesDeCoordenadorGeral()) {
            throw new IllegalStateException("Só a Coordenação Geral pode gerar um link de nova senha.");
        }
        Usuario usuario = buscar(usuarioId);
        usuario.setResetSenhaToken(UUID.randomUUID().toString());
        usuario.setResetSenhaExpiraEm(LocalDateTime.now().plusHours(24));
        usuarioRepository.save(usuario);
        return emailService.linkRedefinicao(usuario.getResetSenhaToken());
    }

    @Transactional
    public void esqueciSenha(String email) {
        // Não revela se o e-mail existe ou não — sempre "sucesso" do ponto de vista de quem pediu.
        usuarioRepository.findByEmail(Usuario.normalizarEmail(email)).ifPresent(usuario -> {
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

    @Transactional
    public GoogleAuthResponseDTO autenticarComGoogle(GoogleAuthDTO dados) {
        GoogleTokenService.GoogleClaims claims = googleTokenService.verificar(dados.idToken());

        String email = Usuario.normalizarEmail(claims.email());

        var existente = usuarioRepository.findByEmail(email);
        if (existente.isPresent()) {
            return new GoogleAuthResponseDTO(false, null, null, loginResponse(existente.get()));
        }

        if (dados.role() == null) {
            // Conta nova: o front ainda precisa perguntar papel + setor antes de criar de verdade.
            return new GoogleAuthResponseDTO(true, claims.nome(), email, null);
        }

        if (dados.role() == Role.COORDENADOR_GERAL && usuarioRepository.existsByRole(Role.COORDENADOR_GERAL)) {
            throw new IllegalStateException("Já existe um Coordenador Geral cadastrado. Fale com essa pessoa pra acessar o sistema.");
        }
        if (dados.role() != Role.COORDENADOR_GERAL && dados.setor() == null) {
            throw new IllegalArgumentException("Selecione o setor.");
        }

        Usuario usuario = Usuario.builder()
                .nome(claims.nome())
                .email(email)
                // Contas via Google não usam senha própria — gera uma aleatória só pra satisfazer a coluna.
                .senha(passwordEncoder.encode(UUID.randomUUID().toString()))
                .funcao(dados.funcao())
                .role(dados.role())
                .setor(dados.role() == Role.COORDENADOR_GERAL ? null : dados.setor())
                .build();

        usuario = usuarioRepository.save(usuario);
        return new GoogleAuthResponseDTO(false, null, null, loginResponse(usuario));
    }

    private LoginResponseDTO loginResponse(Usuario usuario) {
        return new LoginResponseDTO(tokenService.gerarToken(usuario), UsuarioResponseDTO.from(usuario));
    }
}