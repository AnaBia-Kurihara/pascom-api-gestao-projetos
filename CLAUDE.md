# CLAUDE.md

Este arquivo fornece orientações ao Claude Code (claude.ai/code) ao trabalhar com código neste repositório.

## Visão geral do projeto

API de Gestão e Controle de Projetos da Pascom Santuário N. Sra. de Fátima — uma API REST em Spring Boot 3.2 / Java 17 para gerenciar o fluxo de conteúdo de uma equipe de comunicação paroquial: ideias de conteúdo, um quadro de produção estilo Kanban ("cartões") e escala de voluntários para eventos.

O repositório também guarda o front-end em [src/main/resources/static/index.html](src/main/resources/static/index.html) — um app single-file (HTML/CSS/JS puro, sem build), servido automaticamente pelo próprio Spring Boot na raiz (`/`). É por isso que fica dentro de `resources/static`: não é um projeto à parte, é a UI desse mesmo backend. As chamadas de API usam `API_BASE='/api'` (caminho relativo), então funcionam sem alteração tanto local quanto em produção — sobe o backend (`mvn spring-boot:run`) e acessa `http://localhost:8080/`, sem precisar de nenhum servidor separado pro front.

## Comandos

Não há wrapper do Maven versionado (`.mvn/` existe mas está vazio, sem script `mvnw`) — use um `mvn` instalado globalmente.

```bash
mvn spring-boot:run
```

```bash
mvn test
```

```bash
mvn test -Dtest=ClassName#methodName
```

```bash
mvn clean package
```

Atualmente não há classes de teste em `src/test/java` — `mvn test` vai reportar nenhum teste executado.

A aplicação sobe na porta 8080. O console do H2 está disponível em `/h2-console` (URL JDBC `jdbc:h2:file:./data/pascomdb`, usuário `sa`, sem senha), pois `spring.h2.console.enabled=true` em [application.yml](src/main/resources/application.yml).

## Arquitetura

Estrutura em camadas padrão do Spring MVC sob `br.org.pascom`:

- `controller/` — `@RestController`s, um por agregado (`Usuario`, `Evento`, `Cartao`, `Ideia`), todos montados sob `/api/**`. Controllers apenas traduzem HTTP <-> DTO e delegam para os services; nenhuma regra de negócio aqui.
- `service/` — regras de negócio e orquestração. Os services dependem dos repositories diretamente (sem camada separada de mapper); a conversão DTO <-> entidade acontece via métodos estáticos de fábrica nos próprios DTOs (ex.: `UsuarioResponseDTO.from(usuario)`) ou inline no service.
- `repository/` — interfaces `JpaRepository` simples, sem lógica de query customizada além de queries derivadas estilo `findByEmail`.
- `model/` — entidades JPA (Lombok `@Getter/@Setter/@Builder`), além de `model/enums/` para `Role`, `Etapa`, `Formato`, `SlotTipo`.
- `dto/` — `record`s Java para requests/responses, com anotações de validação (`jakarta.validation`).
- `config/` — `WebConfig` (CORS global em `/api/**`, atualmente `allowedOriginPatterns("*")`), `SecurityConfig`/`SecurityFilter` (autenticação stateless via JWT, ver abaixo) e `ApiExceptionHandler` (`@RestControllerAdvice` que traduz `IllegalStateException`/`IllegalArgumentException`/erros de validação/`AuthenticationException` em corpos JSON `400`/`401`). Em `SecurityConfig`, só `/api/**` exige autenticação (exceto cadastro/login) — o front-end estático é sempre público, já que a autenticação de verdade acontece nas chamadas de API que ele faz, não no carregamento da página.

### Autenticação

A API usa **JWT stateless** via Spring Security. `POST /api/usuarios/login` autentica com `AuthenticationManager` e devolve um `LoginResponseDTO{token, usuario}`; o front deve enviar esse token em todas as demais chamadas via header `Authorization: Bearer <token>`. `SecurityFilter` ([SecurityFilter.java](src/main/java/br/org/pascom/config/SecurityFilter.java)) lê e valida o token em cada request e popula o `SecurityContext`; `TokenService` ([TokenService.java](src/main/java/br/org/pascom/service/TokenService.java)) gera/valida o JWT (segredo em `api.security.token.secret`, sobrescrevível pela env var `JWT_SECRET` — **troque o valor padrão antes de produção**). Apenas `POST /api/usuarios/cadastro`, `POST /api/usuarios/login`, `POST /api/usuarios/esqueci-senha`, `POST /api/usuarios/redefinir-senha` e `POST /api/usuarios/google` são públicos; todo o resto de `/api/**` exige token válido.

- **Recuperação de senha**: `POST /api/usuarios/esqueci-senha {email}` gera um token (`Usuario.resetSenhaToken`, válido 1h) e manda um e-mail com o link `/?resetToken=...` via `EmailService` — sempre responde `200`, mesmo se o e-mail não existir (evita enumerar contas). `POST /api/usuarios/redefinir-senha {token, novaSenha}` troca a senha e limpa o token. O front detecta `?resetToken=` na URL (`init()`) e mostra a tela de nova senha.
- **Login/cadastro com Google**: `POST /api/usuarios/google {idToken, role?, setor?, funcao?}` — `GoogleTokenService` valida o ID token direto no endpoint público `oauth2.googleapis.com/tokeninfo` (confere `aud` contra `api.security.google.client-id` / env var `GOOGLE_CLIENT_ID`, e `email_verified`). Se já existe um `Usuario` com aquele e-mail, loga direto; se não existe e `role` veio `null`, devolve `{precisaCompletarCadastro:true, nome, email}` pro front pedir papel+setor antes de chamar o endpoint de novo (dessa vez com `role`/`setor` preenchidos, criando a conta com senha aleatória — essas contas nunca logam por senha, só por Google, a menos que peçam redefinição). O front carrega o script `accounts.google.com/gsi/client` e usa `google.accounts.id` (Google Identity Services); o Client ID **não é secreto** e fica hardcoded em `index.html` (constante `GOOGLE_CLIENT_ID`) além de configurado no backend.

Nos controllers, o usuário autenticado é injetado via `@AuthenticationPrincipal Usuario` — os métodos de service que representam "quem está fazendo a ação" (`moverEtapa`, `adicionarComentario`, `alterarRole`, `inscreverVoluntario`, criar/votar `Ideia`) recebem esse `Usuario` diretamente, e não mais um ID enviado pelo cliente. Respostas de erro de autenticação (401) e autorização (403) seguem o mesmo formato `{"erro": "..."}` do restante da API, configurado em `SecurityConfig.exceptionHandling`.

### Modelo de domínio

- **Usuario**: um membro da equipe; implementa `UserDetails` do Spring Security (o `Role` vira a authority `ROLE_<role>`). Reflete a estrutura real da pastoral: `Role` é `COORDENADOR_GERAL` (coordena a pastoral inteira, todos os setores — não tem `Setor`, o campo fica `null`), `COORDENADOR` (coordena um `Setor` específico) ou `VOLUNTARIO` (também pertence a um `Setor`). `Setor` é `REDES_SOCIAIS`, `JOVENS`, `DATASHOW` ou `TRANSMISSOES_VIDEOS`. A própria pessoa escolhe papel + setor no cadastro (`POST /api/usuarios/cadastro`, validado em `UsuarioService.cadastrar`: setor é obrigatório pra quem não é `COORDENADOR_GERAL`) — não existe mais a regra antiga de "primeiro usuário vira admin automaticamente". Em compensação, **só pode existir um `COORDENADOR_GERAL` no sistema**: `cadastrar` bloqueia (`IllegalStateException`) qualquer tentativa de cadastro com esse papel se `usuarioRepository.existsByRole(COORDENADOR_GERAL)` já for verdadeiro — é a primeira pessoa a escolher esse papel que "ocupa a vaga", ninguém mais consegue depois. `COORDENADOR` (de setor) e `VOLUNTARIO` continuam livres pra qualquer um se cadastrar, sem limite. Só `COORDENADOR_GERAL` pode alterar a função de outro usuário ([UsuarioService.java](src/main/java/br/org/pascom/service/UsuarioService.java)). Senhas são hasheadas com BCrypt e marcadas com `@JsonIgnore`.
- **Ideia**: uma ideia de conteúdo, pertence a um `Setor` (igual `Cartao`, ver abaixo). Qualquer um pode criar e votar (alternar voto) em ideias. Uma ideia pode ser "adotada" (`adotada = true`), o que a converte 1:1 em um novo `Cartao` no mesmo setor, na etapa `IDEIA`, via `IdeiaService.transformarEmCartao` — bloqueado com `IllegalStateException` se a ideia já tiver sido adotada antes (evita cartão duplicado).
- **Cartao**: um cartão de produção que percorre o pipeline de `Etapa`: `IDEIA -> ROTEIRO -> GRAVACAO -> EDICAO -> REVISAO -> AGENDADO -> PUBLICADO`. Qualquer usuário autenticado pode criar cartões, avançar etapas e agendar/publicar — não há checagem de papel em `CartaoService.moverEtapa`. A única trava é de qualidade, não de permissão: avançar para `AGENDADO` ou `PUBLICADO` exige que o `Checklist` embutido no cartão (5 flags booleanas de segurança/revisão) esteja totalmente completo (`Checklist.isCompleto()`). Cada `Cartao` pertence a um `Setor` (campo nullable no banco, mas obrigatório via `@NotNull` no `CartaoRequestDTO` — nullable no schema pra nunca travar uma migração em produção, ver "Navegação por setor" abaixo).
- **Evento** / **SlotEscala**: um evento com uma lista de "slots" de voluntários (`SlotTipo`: `FOTO`, `VIDEO`, `STORIES_AO_VIVO`, `TRANSMISSAO`). **Não pertence a um setor** — eventos/escalas são compartilhados entre toda a pastoral, de propósito (ex.: um bazar pode envolver várias equipes). Só `COORDENADOR_GERAL` pode criar eventos/marcar datas no calendário (`EventoService.criar`) — os `COORDENADOR` de setor ainda não têm esse poder; voluntários (qualquer papel) se auto-inscrevem em um slot específico (`EventoService.inscreverVoluntario`) ou se desinscrevem (`desinscreverVoluntario`). Ambos validam que o `slotId` realmente pertence ao `eventoId` informado; `inscreverVoluntario` também bloqueia se o slot já tiver outro voluntário (evita sobrescrever silenciosamente quem já estava escalado) ou se o próprio voluntário já estiver em outro slot do mesmo evento.
- **Comentario**: comentários em thread anexados a um `Cartao`, de autoria de um `Usuario`. Sempre salvo via `ComentarioRepository` diretamente (não por cascade do `Cartao`), pra garantir que o `id` (IDENTITY) já volte preenchido na resposta.
- **ContaInstagram** / **MetricaPostagem**: estrutura para a integração com a Instagram Graph API — ver seção própria abaixo.

### Navegação por setor

O front-end tem um seletor de "espaço" na barra lateral (`setorSwitchHtml()`, estado `currentSetor`/`setorMenuOpen` em `index.html`) que funciona como um workspace switcher (estilo Notion/Slack): troca entre os 4 setores da pastoral e filtra Painel, Produção (quadro Kanban), Calendário e Ideias pra mostrar só o conteúdo daquele setor — cada espaço é totalmente isolado (cartões/ideias de um setor nunca aparecem em outro). `Equipe` continua mostrando a carga de trabalho de todo mundo em todos os setores (visão geral, de propósito — não é "dentro" de um espaço). Ao logar, `currentSetor` começa no setor da própria pessoa (`me().setor`), ou `redes_sociais` para o `COORDENADOR_GERAL`. Criar um cartão/ideia sempre usa o `currentSetor` do momento (`cartaoToApi`/`addIdea`); a API filtra por setor tanto no carregamento (client-side, via `visibleCards()`/`visibleIdeas()`) quanto opcionalmente no backend (`GET /api/cartoes?setor=X` e `GET /api/ideias?setor=X`, ainda não usados pelo front, que prefere carregar tudo uma vez e filtrar localmente pra trocar de espaço instantaneamente sem round-trip).

**Conhecido em aberto**: o seletor de espaço fica escondido no mobile (`.setor-switch{display:none}` no media query) — no celular dá pra ver em qual setor você está (aparece uma pílula ao lado do título), mas ainda não dá pra trocar de setor pelo celular. Também ainda não foi feito deploy dessa funcionalidade pra produção (testada só localmente) — veja o histórico de commits pra status atualizado.

### Lixeira (exclusão suave)

`Cartao`, `Ideia` e `Evento` usam **exclusão suave**: excluir nunca apaga a linha do banco, só preenche `excluidoEm`/`motivoExclusao`/`detalheExclusao` (enum `MotivoExclusao`: `NAO_DEU_TEMPO`, `JA_PASSOU`, `DUPLICADO`, `MUDANCA_DE_PLANOS`, `NAO_APROVADO`, `OUTRO`). Todo listing normal (`listarTodos`/`listarPorSetor` nos três services) filtra `excluidoEmIsNull`; a lixeira usa as queries complementares (`...ExcluidoEmIsNotNull...`). Endpoints, iguais nos três controllers: `POST /{id}/lixeira` (body `{motivo, detalhe}`, via `ExclusaoRequestDTO`) move pra lixeira, `POST /{id}/restaurar` traz de volta, `GET /lixeira` lista os excluídos (`?setor=X` pra Cartão/Ideia — `Evento` não é filtrado por setor, ver acima). Restaurar/excluir `Evento` exige `temPoderesDeCoordenadorGeral()` (mesma regra de `criar`); Cartão e Ideia continuam sem checagem de papel, igual o resto do fluxo desses dois agregados.

No front, a aba **Lixeira** (`viewLixeira()`) é sector-scoped (Cartões/Ideias do `currentSetor`) mas sempre mostra todas as Escalas excluídas (já que `Evento` é global). Excluir qualquer um dos três tipos abre um modal (`abrirExclusao`/`motivoExclusaoModal`) pedindo o motivo antes de confirmar — não existe mais exclusão direta sem motivo. **Cuidado ao mexer em `EventoService.gerarEscalasDominicaisDoMes`**: o check de duplicidade usa `existsByDataAndTituloAndExcluidoEmIsNull` (não `existsByDataAndTitulo`) de propósito, senão um domingo cujo evento foi pra lixeira nunca seria regerado automaticamente no mês seguinte.

### Integração com Instagram

**Validada de ponta a ponta com a conta real do santuário** (`@santuariodefatima`) via `InstagramService`/`InstagramController`. Usa o fluxo **"Instagram API with Instagram Login"** (Business Login for Instagram) — não exige Página do Facebook vinculada, só que a conta seja Business/Creator. O app Meta é o "Pascom Fátima - Integração" (App ID `1858551365508382`).
- `ContaInstagram` guarda a conexão (id fixo `1L`, uma linha só — é a conta única do santuário). O `accessToken` é sempre criptografado (`CriptografiaService`, AES-GCM com chave em `api.security.instagram.chave` / env var `INSTAGRAM_TOKEN_KEY`) antes de ir pro banco, e nunca é exposto pela API (`@JsonIgnore`).
- `POST /api/instagram/conectar` / `DELETE /api/instagram/desconectar` — só `COORDENADOR_GERAL`. Hoje o token é colado manualmente (gerado em Meta for Developers → Casos de uso → "Configuração da API com login do Instagram" → "Gerar tokens de acesso", exige a conta estar cadastrada como "Testador do Instagram" em Funções do app e aceitar o convite); quando o fluxo OAuth completo for implementado, isso passa a vir automaticamente do callback.
- **Importante**: os tokens desse fluxo começam com `IGAA` e só funcionam contra o host **`graph.instagram.com`** (não `graph.facebook.com`, que devolve "Cannot parse access token" pra esse tipo de token). Isso já está correto em `InstagramService.GRAPH_BASE_URL`.
- `Cartao.instagramMediaId`/`instagramPermalink` — vincula manualmente um cartão publicado ao post real (`PATCH /api/cartoes/{id}/instagram`), já que o app não publica direto no Instagram (isso teria muito mais restrições de tipo de conteúdo/limite de posts).
- `MetricaPostagem` é uma tabela de **histórico** (uma linha por coleta, não um valor fixo) — a Graph API só guarda métricas por 90 dias, então a projeção de crescimento de longo prazo depende desse histórico no nosso banco. `InstagramService.coletarMetricas` chama a Graph API de verdade (`GET /{media-id}/insights?metric=likes,comments,saved,shares,reach`) e grava uma nova linha; `coletarMetricasDiarias` (`@Scheduled`, 6h da manhã) faz isso automaticamente pra todo cartão `PUBLICADO` com `instagramMediaId` preenchido, sem interromper a coleta se um post isolado falhar.
- Métricas confirmadas funcionando nesses 5 nomes (`InstagramService.METRICAS`): `likes`, `comments`, `saved`, `shares`, `reach` — testado contra um carrossel real.

### Assistente de IA (chat)

Aba "Assistente" no front (`viewAssistente`/`chatLog` em `index.html`) — um chat com design próprio (bolhas, indicador de "digitando", sugestões, ações executadas destacadas) que conversa em português e **executa ações reais** no sistema via `POST /api/agente/mensagem` (`AgenteController` → `AgenteService`).

- `AgenteService` usa o **Gemini** (`gemini-2.0-flash`, chamado via `RestClient` cru — sem SDK) com **function calling**: manda o histórico da conversa + a lista de "ferramentas" (`ferramentas()`), e fica em loop (até `MAX_RODADAS_FERRAMENTA = 6`) enquanto o modelo pedir pra chamar alguma; quando ele responde só texto (sem `functionCall`), a conversa termina e isso vira a resposta final.
- Ferramentas disponíveis: `criar_evento` (só funciona se quem está no chat for `COORDENADOR_GERAL` — mesma regra do `EventoService.criar`; se der erro de permissão, o próprio prompt do sistema instrui o modelo a explicar com gentileza e não insistir), `criar_ideia` (qualquer um, sempre no `Setor` que o front está mostrando no momento — ver "Navegação por setor"), `listar_eventos` e `listar_ideias` (pro modelo conferir o que já existe antes de criar coisas novas, evitando duplicar — ex.: ao pedir "adicione os santos de outubro", o modelo usa o próprio conhecimento do calendário litúrgico católico pra escolher as datas e chama `criar_evento` uma vez por data).
- Cada chamada de ferramenta é executada de verdade contra os services existentes (não é simulação) e respeita as mesmas regras de negócio/permissão dos endpoints normais — erros (`IllegalStateException`/`IllegalArgumentException`) viram uma `functionResponse` de erro que o modelo recebe de volta e pode explicar ao usuário.
- Configuração: chave em `api.gemini.chave` / env var `GEMINI_API_KEY` (gerada em aistudio.google.com/apikey). Sem chave configurada, o endpoint devolve `IllegalStateException` com mensagem amigável em vez de tentar chamar a API.
- O front manda o `Setor` atual (`currentSetor`) em toda mensagem (`AgenteMensagemDTO.setor`), pra `criar_ideia` e o prompt do sistema saberem em qual "espaço" a conversa está acontecendo.

### Persistência

Por padrão (sem profile ativo, uso local/dev), banco H2 baseado em arquivo em `./data/pascomdb` (ignorado pelo git) com `ddl-auto: update` — o schema evolui automaticamente a partir das entidades, sem scripts de migração (sem Flyway/Liquibase). O SQL é logado (`show-sql: true`).

### Deploy / produção

Existe um profile `prod` em [application.yml](src/main/resources/application.yml) (ativado com a env var `SPRING_PROFILES_ACTIVE=prod`) que troca o H2 por Postgres de verdade, lendo `PGHOST`/`PGPORT`/`PGDATABASE`/`PGUSER`/`PGPASSWORD` — o padrão de variáveis que provedores como Railway já injetam automaticamente ao conectar um banco Postgres ao serviço — e desliga o console do H2. Em produção, defina também `JWT_SECRET`, `INSTAGRAM_TOKEN_KEY`, `RESEND_API_KEY`, `APP_BASE_URL`, `GOOGLE_CLIENT_ID` e `GEMINI_API_KEY` (os defaults no `application.yml` são só pra dev local). `server.port` lê a env var `PORT` quando presente (padrão que a maioria dos PaaS usa), com fallback pra `8080`.

Hospedado no **Railway** (projeto `overflowing-empathy`, serviço `pascom-api-gestao-projetos`). A integração automática Railway↔GitHub está **quebrada** (o GitHub App foi desinstalado da conta em algum momento; "Auto deploy unavailable" / "Could not load branches" nas configurações do serviço) — por enquanto o deploy é sempre manual via **Railway CLI** direto da máquina local:
```bash
railway up --ci --message "descrição do que mudou"
```
(`railway login` uma vez, `railway link` já feito na pasta do projeto). Isso builda com Maven dentro do Railway e sobe uma imagem nova — não depende do GitHub, mas ainda assim vale sempre dar `git push` pra manter o repositório em dia.

**Cuidado com renomear valores de enum que já têm dados em produção**: o Postgres do Railway tem `CHECK CONSTRAINT`s gerados pelo Hibernate a partir dos enums Java (ex.: `tb_usuarios_role_check`), e `ddl-auto: update` **não** atualiza essas constraints sozinho quando um enum muda — elas continuam com os valores antigos e todo INSERT/UPDATE com um valor novo quebra com `violates check constraint`. Depois de renomear um enum, é preciso rodar manualmente (via `railway connect postgres --tunnel-only` + `psql`, já que os consoles SQL do próprio painel do Railway se mostraram instáveis nesse projeto):
```sql
ALTER TABLE tb_usuarios DROP CONSTRAINT tb_usuarios_role_check;
ALTER TABLE tb_usuarios ADD CONSTRAINT tb_usuarios_role_check CHECK (role IN ('NOVO_VALOR_1','NOVO_VALOR_2'));
```

**E-mail transacional (recuperação de senha) usa a API HTTPS do Resend, não SMTP** — confirmado testando com `railway ssh` que o Railway bloqueia as portas de saída 25/465/587 (SMTP), inclusive pra Gmail com senha de app; só HTTPS (443) funciona. `EmailService` chama `api.resend.com/emails` direto via `RestClient` (mesmo padrão de HTTP cru já usado pra Instagram/Google), com a chave em `api.resend.chave` / env var `RESEND_API_KEY` e remetente em `api.resend.remetente` / `RESEND_FROM` (default `onboarding@resend.dev`, funciona sem verificar domínio próprio).

### Convenção de tratamento de erros

Violações de regra de negócio devem lançar `IllegalStateException` (transição de estado inválida, permissão negada) ou `IllegalArgumentException` (entidade não encontrada, input inválido) a partir dos services — ambas são capturadas centralmente pelo `ApiExceptionHandler` e retornadas como `400` com `{"erro": "..."}`. Falhas de Bean Validation em DTOs `@Valid @RequestBody` são capturadas separadamente e retornadas como `{"erros": {campo: mensagem}}`.
