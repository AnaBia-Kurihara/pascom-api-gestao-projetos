package br.org.pascom.service;

import br.org.pascom.dto.GoogleAuthDTO;
import br.org.pascom.dto.GoogleAuthResponseDTO;
import br.org.pascom.dto.LoginResponseDTO;
import br.org.pascom.dto.PapelUpdateDTO;
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
import java.util.Set;
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

    private final AvisoService avisoService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager, TokenService tokenService,
                           EmailService emailService, GoogleTokenService googleTokenService,
                           FotoUsuarioRepository fotoUsuarioRepository, AvisoService avisoService) {
        this.avisoService = avisoService;
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

    /** A lista da equipe, sem o WhatsApp dos outros (só o próprio e o de quem já aparece no "Precisa de ajuda?"). */
    public List<UsuarioResponseDTO> listarTodos(Usuario quemVe) {
        return usuarioRepository.findAll().stream()
                .map(u -> UsuarioResponseDTO.from(u, (quemVe != null && u.getId().equals(quemVe.getId()))
                        || Boolean.TRUE.equals(u.getWhatsappNaAjuda())))
                .toList();
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
        Usuario.validarAniversario(dados.aniversarioDia(), dados.aniversarioMes());

        Usuario usuario = Usuario.builder()
                .nome(dados.nome())
                .aniversarioDia(dados.aniversarioDia())
                .aniversarioMes(dados.aniversarioMes())
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

    public String renovarToken(Usuario usuario) {
        return tokenService.gerarToken(buscar(usuario.getId()));
    }

    /** Domínio dos e-mails de mentira de quem foi cadastrado sem e-mail (".invalid" nunca existe de verdade). */
    public static final String DOMINIO_SEM_EMAIL = "@sem-email.pascom.invalid";

    /**
     * A coordenação cadastra um voluntário que não conseguiu se cadastrar sozinho, já no setor dela,
     * pra poder escalar. Sem e-mail, a pessoa fica com um e-mail de mentira (nunca recebe nada e não
     * entra no app); com e-mail, depois ela entra por "Entrar com Google" ou "Esqueci a senha".
     */
    @Transactional
    public UsuarioResponseDTO cadastrarPelaCoordenacao(br.org.pascom.dto.CadastroPelaCoordenacaoDTO dados, Usuario solicitante) {
        boolean geral = solicitante.temPoderesDeCoordenadorGeral() || solicitante.gereTodasAsEscalas();
        if (!geral && solicitante.getRole() != Role.COORDENADOR) {
            throw new IllegalStateException("Só a coordenação pode cadastrar voluntários.");
        }
        // A Geral escolhe; o coordenador cadastra no setor dele (ou no "irmão": Datashow ↔ Holyrics).
        Setor setor = geral ? dados.setor()
                : dados.setor() != null && geraEquipe(solicitante, dados.setor()) ? dados.setor() : solicitante.getSetor();
        if (setor == null) throw new IllegalArgumentException("Escolha o setor.");
        String nome = dados.nome() == null ? "" : dados.nome().trim().replaceAll("\\s+", " ");
        if (nome.isEmpty()) throw new IllegalArgumentException("Escreva o nome da pessoa.");

        String email;
        if (dados.email() == null || dados.email().isBlank()) {
            email = "sem-email-" + UUID.randomUUID().toString().substring(0, 12) + DOMINIO_SEM_EMAIL;
        } else {
            email = Usuario.normalizarEmail(dados.email());
            if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("Esse e-mail não parece certo.");
            usuarioRepository.findByEmail(email).ifPresent(u -> {
                throw new IllegalStateException(u.getNome() + " já tem cadastro com esse e-mail. Use \"Incluir pessoa na equipe\".");
            });
        }
        Usuario.validarAniversario(dados.aniversarioDia(), dados.aniversarioMes());

        Usuario usuario = Usuario.builder()
                .nome(nome)
                .email(email)
                // Senha aleatória: ninguém sabe. Pra entrar, a pessoa usa o Google ou "Esqueci a senha".
                .senha(passwordEncoder.encode(UUID.randomUUID() + "-" + UUID.randomUUID()))
                .funcao(dados.funcao() == null || dados.funcao().isBlank() ? null : dados.funcao().trim())
                .aniversarioDia(dados.aniversarioDia())
                .aniversarioMes(dados.aniversarioMes())
                .role(Role.VOLUNTARIO)
                .setor(setor)
                .build();
        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    /** Erros de senha por e-mail: depois de {@link #MAX_ERROS_SENHA} em 15 min, aquele e-mail espera 15 min. */
    private static final int MAX_ERROS_SENHA = 8;
    private static final java.time.Duration BLOQUEIO_SENHA = java.time.Duration.ofMinutes(15);
    private record ErrosSenha(int quantos, java.time.Instant desde) {}
    private final java.util.Map<String, ErrosSenha> errosSenha = new java.util.concurrent.ConcurrentHashMap<>();

    public LoginResponseDTO login(String email, String senha) {
        String chave = Usuario.normalizarEmail(email);
        java.time.Instant agora = java.time.Instant.now();
        errosSenha.values().removeIf(e -> e.desde().plus(BLOQUEIO_SENHA).isBefore(agora));
        ErrosSenha erros = errosSenha.get(chave);
        if (erros != null && erros.quantos() >= MAX_ERROS_SENHA) {
            throw new IllegalStateException("Muitas tentativas com senha errada. Espere 15 minutos ou use \"Esqueci a senha\".");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(chave, senha));
        } catch (org.springframework.security.core.AuthenticationException e) {
            errosSenha.merge(chave, new ErrosSenha(1, agora), (a, b) -> new ErrosSenha(a.quantos() + 1, a.desde()));
            throw e;
        }
        errosSenha.remove(chave);

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
        if (dados.aniversarioDia() != null || dados.aniversarioMes() != null) {
            Usuario.validarAniversario(dados.aniversarioDia(), dados.aniversarioMes());
            usuario.setAniversarioDia(dados.aniversarioDia());
            usuario.setAniversarioMes(dados.aniversarioMes());
        }

        EnumSet<Setor> extras = EnumSet.noneOf(Setor.class);
        // Coordenador Geral já faz parte de todos os setores; o principal não precisa repetir.
        if (dados.setoresExtras() != null && usuario.getRole() != Role.COORDENADOR_GERAL) {
            dados.setoresExtras().stream()
                    .filter(s -> s != null && s != usuario.getSetor())
                    .forEach(extras::add);
        }
        usuario.getSetoresExtras().clear();
        usuario.getSetoresExtras().addAll(extras);

        // null = a tela não mandou (versão antiga do app): não mexe no que já estava salvo.
        if (dados.whatsapp() != null) usuario.setWhatsapp(normalizarWhatsapp(dados.whatsapp()));
        if (dados.whatsappNaAjuda() != null) usuario.setWhatsappNaAjuda(dados.whatsappNaAjuda());

        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    /** Só dígitos, com o 55 do Brasil na frente; vazio = sem WhatsApp. */
    static String normalizarWhatsapp(String w) {
        String d = w == null ? "" : w.replaceAll("\\D", "");
        if (d.isEmpty()) return null;
        if (d.length() == 10 || d.length() == 11) d = "55" + d;
        if (d.length() < 12 || d.length() > 13) {
            throw new IllegalArgumentException("Esse WhatsApp não parece certo. Use DDD + número, ex.: (11) 91234-5678.");
        }
        return d;
    }

    /**
     * Contatos do "Precisa de ajuda?" (tela de entrada, sem login): só quem é da coordenação, deixou o
     * WhatsApp e marcou pra aparecer. "app" = Coordenação Geral (ajuda com o app); "setores" = coordenação
     * de cada setor (inclui quem coordena pela flag de gestor de escalas como contato de todos).
     */
    public java.util.Map<String, Object> contatosDeAjuda() {
        List<java.util.Map<String, Object>> app = new java.util.ArrayList<>();
        java.util.Map<Setor, List<java.util.Map<String, Object>>> porSetor = new java.util.EnumMap<>(Setor.class);
        for (Usuario u : usuarioRepository.findAll()) {
            if (!Boolean.TRUE.equals(u.getWhatsappNaAjuda()) || u.getWhatsapp() == null) continue;
            java.util.Map<String, Object> c = java.util.Map.of("nome", u.getNome(), "whatsapp", u.getWhatsapp());
            if (u.temPoderesDeCoordenadorGeral()) app.add(c);
            if (u.getRole() == Role.COORDENADOR && u.getSetor() != null) porSetor.computeIfAbsent(u.getSetor(), k -> new java.util.ArrayList<>()).add(c);
            if (u.gereTodasAsEscalas() && !u.temPoderesDeCoordenadorGeral()) {
                for (Setor s : Setor.values()) porSetor.computeIfAbsent(s, k -> new java.util.ArrayList<>()).add(c);
            }
        }
        List<java.util.Map<String, Object>> setores = new java.util.ArrayList<>();
        for (Setor s : Setor.values()) {
            setores.add(java.util.Map.of("setor", s, "nomeSetor", nomeSetor(s), "contatos", porSetor.getOrDefault(s, List.of())));
        }
        return java.util.Map.of("app", app, "setores", setores);
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

    /**
     * Coordenação Geral muda o papel e o setor principal de alguém (ex.: promover a voluntária que
     * na verdade é subcoordenadora). Só pode existir um COORDENADOR_GERAL, e ninguém muda o
     * próprio papel (evita a Coordenação Geral se trancar pra fora sem querer).
     */
    /**
     * A coordenação inclui ou tira alguém da equipe de um setor (setor extra), ex.: pôr o Holyrics pra
     * quem já é do Datashow. Coordenação Geral e gestor(a) de escalas mexem em qualquer setor; o
     * coordenador de setor, só no dele. O setor principal continua sendo mudado só no "Gerenciar".
     */
    @Transactional
    public UsuarioResponseDTO alterarSetorExtra(Long id, Setor setor, boolean incluir, Usuario solicitante) {
        if (setor == null) throw new IllegalArgumentException("Escolha o setor.");
        if (!geraEquipe(solicitante, setor)) {
            throw new IllegalStateException("Só a coordenação de " + nomeSetor(setor) + " pode mudar quem faz parte dessa equipe.");
        }
        Usuario usuario = buscar(id);
        if (usuario.getRole() == Role.COORDENADOR_GERAL) {
            throw new IllegalStateException("A Coordenação Geral já faz parte de todos os setores.");
        }
        boolean mudou;
        String extra = "";
        if (usuario.getSetor() == setor) {
            if (incluir) return UsuarioResponseDTO.from(usuario);
            // Tirar do setor principal: um dos outros setores dela vira o principal.
            if (usuario.getRole() == Role.COORDENADOR) {
                throw new IllegalStateException(usuario.getNome() + " é coordenador(a) de " + nomeSetor(setor)
                        + ". Só a Coordenação Geral muda isso, em Gerenciar.");
            }
            Setor novo = usuario.getSetoresExtras().stream().sorted().findFirst().orElseThrow(() -> new IllegalStateException(
                    usuario.getNome() + " só faz parte de " + nomeSetor(setor) + ". Pra tirar, a Coordenação Geral muda o setor "
                            + "dela em Gerenciar (ou inclua antes em outro setor)."));
            usuario.getSetoresExtras().remove(novo);
            usuario.setSetor(novo);
            mudou = true;
            extra = " Seu setor agora é " + nomeSetor(novo) + ".";
        } else {
            mudou = incluir ? usuario.getSetoresExtras().add(setor) : usuario.getSetoresExtras().remove(setor);
        }
        usuarioRepository.save(usuario);
        if (mudou && !usuario.getId().equals(solicitante.getId())) {
            avisoService.avisar(usuario, incluir
                    ? "🎉 " + solicitante.getNome() + " incluiu você na equipe de " + nomeSetor(setor) + ". Agora você vê as escalas desse setor e pode se candidatar."
                    : solicitante.getNome() + " tirou você da equipe de " + nomeSetor(setor) + "." + extra);
        }
        return UsuarioResponseDTO.from(usuario);
    }

    /** Datashow e Holyrics trabalham juntos: quem coordena um cuida da equipe dos dois. */
    private static final java.util.Map<Setor, Set<Setor>> SETORES_IRMAOS = java.util.Map.of(
            Setor.DATASHOW, Set.of(Setor.HOLYRICS), Setor.HOLYRICS, Set.of(Setor.DATASHOW));

    /** Quem pode incluir, tirar e alterar pessoas na equipe do setor. */
    static boolean geraEquipe(Usuario u, Setor setor) {
        if (u == null || setor == null) return false;
        if (u.temPoderesDeCoordenadorGeral() || u.gereTodasAsEscalas()) return true;
        return u.getRole() == Role.COORDENADOR && u.getSetor() != null
                && (u.getSetor() == setor || SETORES_IRMAOS.getOrDefault(u.getSetor(), Set.of()).contains(setor));
    }

    /**
     * A coordenação corrige nome, função e disponibilidade de um voluntário da equipe dela (ex.: quem
     * foi cadastrado pela coordenação). Coordenadores e a Geral só são alterados pela Coordenação Geral.
     */
    @Transactional
    public UsuarioResponseDTO alterarDadosNaEquipe(Long id, String nome, String funcao, String disponibilidade, Usuario solicitante) {
        Usuario usuario = buscar(id);
        boolean geral = solicitante.temPoderesDeCoordenadorGeral();
        Set<Setor> setoresDela = new java.util.HashSet<>(usuario.getSetoresExtras());
        if (usuario.getSetor() != null) setoresDela.add(usuario.getSetor());
        boolean pode = geral || (usuario.getRole() == Role.VOLUNTARIO && setoresDela.stream().anyMatch(s -> geraEquipe(solicitante, s)));
        if (!pode) {
            throw new IllegalStateException("Você só pode alterar os dados de voluntários da sua equipe.");
        }
        if (nome != null) {
            String n = nome.trim().replaceAll("\\s+", " ");
            if (n.isEmpty()) throw new IllegalArgumentException("O nome não pode ficar vazio.");
            if (n.length() > 120) throw new IllegalArgumentException("Nome grande demais.");
            usuario.setNome(n);
        }
        if (funcao != null) usuario.setFuncao(funcao.isBlank() ? null : funcao.trim().substring(0, Math.min(120, funcao.trim().length())));
        if (disponibilidade != null) usuario.setDisponibilidade(disponibilidade.isBlank() ? null
                : disponibilidade.trim().substring(0, Math.min(200, disponibilidade.trim().length())));
        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    private static String nomeSetor(Setor s) {
        return EventoService.nomeSetor(s);
    }

    @Transactional
    public UsuarioResponseDTO alterarPapel(Long id, PapelUpdateDTO dados, Usuario solicitante) {
        if (!solicitante.temPoderesDeCoordenadorGeral()) {
            throw new IllegalStateException("Só a Coordenação Geral pode mudar o papel de alguém da equipe.");
        }
        if (solicitante.getId().equals(id)) {
            throw new IllegalStateException("Você não pode mudar o seu próprio papel.");
        }
        Usuario usuario = buscar(id);

        if (dados.role() == Role.COORDENADOR_GERAL) {
            usuarioRepository.findAll().stream()
                    .filter(u -> u.getRole() == Role.COORDENADOR_GERAL && !u.getId().equals(id))
                    .findFirst()
                    .ifPresent(outro -> {
                        throw new IllegalStateException("Já existe um(a) Coordenador(a) Geral: " + outro.getNome() + ".");
                    });
            usuario.setRole(Role.COORDENADOR_GERAL);
            usuario.setSetor(null);
            usuario.getSetoresExtras().clear();
        } else {
            Setor setor = dados.setor() != null ? dados.setor() : usuario.getSetor();
            if (setor == null) {
                throw new IllegalArgumentException("Escolha o setor.");
            }
            usuario.setRole(dados.role());
            usuario.setSetor(setor);
            // O setor principal não precisa aparecer também como extra.
            usuario.getSetoresExtras().remove(setor);
        }
        if (dados.gestorEscalas() != null) {
            usuario.setGestorEscalas(dados.gestorEscalas());
        }
        return UsuarioResponseDTO.from(usuarioRepository.save(usuario));
    }

    /**
     * Coordenação Geral manda um e-mail pra quem ainda não informou o aniversário, pedindo pra
     * preencher no perfil. Só chega de verdade quando o Resend tem um domínio verificado
     * (RESEND_FROM); até lá o próprio app pede no Painel. Devolve quantos foram enviados/falharam.
     */
    public java.util.Map<String, Integer> pedirAniversarios(Usuario solicitante) {
        if (!solicitante.temPoderesDeCoordenadorGeral()) {
            throw new IllegalStateException("Só a Coordenação Geral pode mandar esse pedido.");
        }
        int enviados = 0, falhas = 0;
        for (Usuario u : usuarioRepository.findAll()) {
            if (u.getAniversarioDia() != null && u.getAniversarioMes() != null) continue;
            String texto = "Oi, " + u.getNome().split(" ")[0] + "! Paz e bem.\n\n"
                    + "Estamos registrando o aniversário de todo mundo da Pascom no app, pra celebrar cada um no seu dia. "
                    + "Pode informar o seu (só dia e mês)? É rapidinho: entre no app, toque na sua foto no topo, "
                    + "vá em \"Meu perfil\" e preencha \"Aniversário\".\n\n" + emailService.linkApp() + "\n\nObrigado(a)!";
            if (emailService.enviar(u.getEmail(), "Pascom Fátima — Quando é o seu aniversário?", texto)) enviados++;
            else falhas++;
            try { Thread.sleep(600); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); } // Resend: até 2/s
        }
        return java.util.Map.of("enviados", enviados, "falhas", falhas);
    }

    @Transactional
    public void esqueciSenha(String email) {
        // Não revela se o e-mail existe ou não — sempre "sucesso" do ponto de vista de quem pediu
        // (nem quando o e-mail falha: a resposta igual pra todo mundo não deixa descobrir quem tem conta).
        usuarioRepository.findByEmail(Usuario.normalizarEmail(email)).ifPresent(usuario -> {
            usuario.setResetSenhaToken(UUID.randomUUID().toString());
            usuario.setResetSenhaExpiraEm(LocalDateTime.now().plusHours(1));
            usuarioRepository.save(usuario);
            if (!emailService.enviarLinkRedefinicaoSenha(usuario.getEmail(), usuario.getResetSenhaToken())) {
                pedirLinkACoordenacaoGeral(usuario);
            }
        });
    }

    /**
     * O e-mail não saiu (enquanto não houver domínio verificado no Resend, ele só chega pro dono da
     * conta): avisa quem pode gerar o link de nova senha (sininho + notificação no celular), com um
     * toque que abre o "Gerar link" daquela pessoa. No máximo um aviso a cada 30 min por pessoa.
     */
    private void pedirLinkACoordenacaoGeral(Usuario quemPediu) {
        String acao = "linkSenha:" + quemPediu.getId();
        for (Usuario u : usuarioRepository.findAll()) {
            if (!u.temPoderesDeCoordenadorGeral() || u.getId().equals(quemPediu.getId())) continue;
            if (avisoService.jaAvisou(u, acao, LocalDateTime.now().minusMinutes(30))) continue;
            avisoService.avisar(u, "🔑 " + quemPediu.getNome() + " esqueceu a senha e o e-mail não chegou. "
                    + "Toque aqui pra gerar o link de nova senha e mandar pelo WhatsApp.", acao);
        }
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

        Usuario.validarAniversario(dados.aniversarioDia(), dados.aniversarioMes());
        Usuario usuario = Usuario.builder()
                .nome(claims.nome())
                .aniversarioDia(dados.aniversarioDia())
                .aniversarioMes(dados.aniversarioMes())
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