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

A API usa **JWT stateless** via Spring Security. `POST /api/usuarios/login` autentica com `AuthenticationManager` e devolve um `LoginResponseDTO{token, usuario}`; o front deve enviar esse token em todas as demais chamadas via header `Authorization: Bearer <token>`. `SecurityFilter` ([SecurityFilter.java](src/main/java/br/org/pascom/config/SecurityFilter.java)) lê e valida o token em cada request e popula o `SecurityContext`; `TokenService` ([TokenService.java](src/main/java/br/org/pascom/service/TokenService.java)) gera/valida o JWT (segredo em `api.security.token.secret`, sobrescrevível pela env var `JWT_SECRET` — **troque o valor padrão antes de produção**). Apenas `POST /api/usuarios/cadastro` e `POST /api/usuarios/login` são públicos; todo o resto de `/api/**` exige token válido. Nos controllers, o usuário autenticado é injetado via `@AuthenticationPrincipal Usuario` — os métodos de service que representam "quem está fazendo a ação" (`moverEtapa`, `adicionarComentario`, `alterarRole`, `inscreverVoluntario`, criar/votar `Ideia`) recebem esse `Usuario` diretamente, e não mais um ID enviado pelo cliente. Respostas de erro de autenticação (401) e autorização (403) seguem o mesmo formato `{"erro": "..."}` do restante da API, configurado em `SecurityConfig.exceptionHandling`.

### Modelo de domínio

- **Usuario**: um membro da equipe; implementa `UserDetails` do Spring Security (o `Role` vira a authority `ROLE_<role>`). Reflete a estrutura real da pastoral: `Role` é `COORDENADOR_GERAL` (coordena a pastoral inteira, todos os setores — não tem `Setor`, o campo fica `null`), `COORDENADOR` (coordena um `Setor` específico) ou `VOLUNTARIO` (também pertence a um `Setor`). `Setor` é `REDES_SOCIAIS`, `JOVENS`, `DATASHOW` ou `TRANSMISSOES_VIDEOS`. A própria pessoa escolhe papel + setor no cadastro (`POST /api/usuarios/cadastro`, validado em `UsuarioService.cadastrar`: setor é obrigatório pra quem não é `COORDENADOR_GERAL`) — não existe mais a regra antiga de "primeiro usuário vira admin automaticamente". Só `COORDENADOR_GERAL` pode alterar a função de outro usuário ([UsuarioService.java](src/main/java/br/org/pascom/service/UsuarioService.java)). Hoje **o app inteiro é focado no setor Redes Sociais** (cartões, ideias, calendário) — os outros três setores existem no cadastro e na Equipe, mas ainda não têm telas/fluxos próprios (trabalho futuro: navegação entre setores, cada um com sua própria programação visível pra todo mundo). Senhas são hasheadas com BCrypt e marcadas com `@JsonIgnore`.
- **Ideia**: uma ideia de conteúdo. Qualquer um pode criar e votar (alternar voto) em ideias. Uma ideia pode ser "adotada" (`adotada = true`), o que a converte 1:1 em um novo `Cartao` na etapa `IDEIA` via `IdeiaService.transformarEmCartao` — bloqueado com `IllegalStateException` se a ideia já tiver sido adotada antes (evita cartão duplicado).
- **Cartao**: um cartão de produção que percorre o pipeline de `Etapa`: `IDEIA -> ROTEIRO -> GRAVACAO -> EDICAO -> REVISAO -> AGENDADO -> PUBLICADO`. Qualquer usuário autenticado pode criar cartões, avançar etapas e agendar/publicar — não há checagem de papel em `CartaoService.moverEtapa`. A única trava é de qualidade, não de permissão: avançar para `AGENDADO` ou `PUBLICADO` exige que o `Checklist` embutido no cartão (5 flags booleanas de segurança/revisão) esteja totalmente completo (`Checklist.isCompleto()`).
- **Evento** / **SlotEscala**: um evento com uma lista de "slots" de voluntários (`SlotTipo`: `FOTO`, `VIDEO`, `STORIES_AO_VIVO`, `TRANSMISSAO`). Só `COORDENADOR_GERAL` pode criar eventos/marcar datas no calendário (`EventoService.criar`) — os `COORDENADOR` de setor ainda não têm esse poder (não há escopo por setor em `Evento` ainda); voluntários (qualquer papel) se auto-inscrevem em um slot específico (`EventoService.inscreverVoluntario`) ou se desinscrevem (`desinscreverVoluntario`). Ambos validam que o `slotId` realmente pertence ao `eventoId` informado; `inscreverVoluntario` também bloqueia se o slot já tiver outro voluntário (evita sobrescrever silenciosamente quem já estava escalado) ou se o próprio voluntário já estiver em outro slot do mesmo evento.
- **Comentario**: comentários em thread anexados a um `Cartao`, de autoria de um `Usuario`. Sempre salvo via `ComentarioRepository` diretamente (não por cascade do `Cartao`), pra garantir que o `id` (IDENTITY) já volte preenchido na resposta.
- **ContaInstagram** / **MetricaPostagem**: estrutura para a integração com a Instagram Graph API — ver seção própria abaixo.

### Integração com Instagram

**Validada de ponta a ponta com a conta real do santuário** (`@santuariodefatima`) via `InstagramService`/`InstagramController`. Usa o fluxo **"Instagram API with Instagram Login"** (Business Login for Instagram) — não exige Página do Facebook vinculada, só que a conta seja Business/Creator. O app Meta é o "Pascom Fátima - Integração" (App ID `1858551365508382`).
- `ContaInstagram` guarda a conexão (id fixo `1L`, uma linha só — é a conta única do santuário). O `accessToken` é sempre criptografado (`CriptografiaService`, AES-GCM com chave em `api.security.instagram.chave` / env var `INSTAGRAM_TOKEN_KEY`) antes de ir pro banco, e nunca é exposto pela API (`@JsonIgnore`).
- `POST /api/instagram/conectar` / `DELETE /api/instagram/desconectar` — só `COORDENADOR_GERAL`. Hoje o token é colado manualmente (gerado em Meta for Developers → Casos de uso → "Configuração da API com login do Instagram" → "Gerar tokens de acesso", exige a conta estar cadastrada como "Testador do Instagram" em Funções do app e aceitar o convite); quando o fluxo OAuth completo for implementado, isso passa a vir automaticamente do callback.
- **Importante**: os tokens desse fluxo começam com `IGAA` e só funcionam contra o host **`graph.instagram.com`** (não `graph.facebook.com`, que devolve "Cannot parse access token" pra esse tipo de token). Isso já está correto em `InstagramService.GRAPH_BASE_URL`.
- `Cartao.instagramMediaId`/`instagramPermalink` — vincula manualmente um cartão publicado ao post real (`PATCH /api/cartoes/{id}/instagram`), já que o app não publica direto no Instagram (isso teria muito mais restrições de tipo de conteúdo/limite de posts).
- `MetricaPostagem` é uma tabela de **histórico** (uma linha por coleta, não um valor fixo) — a Graph API só guarda métricas por 90 dias, então a projeção de crescimento de longo prazo depende desse histórico no nosso banco. `InstagramService.coletarMetricas` chama a Graph API de verdade (`GET /{media-id}/insights?metric=likes,comments,saved,shares,reach`) e grava uma nova linha; `coletarMetricasDiarias` (`@Scheduled`, 6h da manhã) faz isso automaticamente pra todo cartão `PUBLICADO` com `instagramMediaId` preenchido, sem interromper a coleta se um post isolado falhar.
- Métricas confirmadas funcionando nesses 5 nomes (`InstagramService.METRICAS`): `likes`, `comments`, `saved`, `shares`, `reach` — testado contra um carrossel real.

### Persistência

Por padrão (sem profile ativo, uso local/dev), banco H2 baseado em arquivo em `./data/pascomdb` (ignorado pelo git) com `ddl-auto: update` — o schema evolui automaticamente a partir das entidades, sem scripts de migração (sem Flyway/Liquibase). O SQL é logado (`show-sql: true`).

### Deploy / produção

Existe um profile `prod` em [application.yml](src/main/resources/application.yml) (ativado com a env var `SPRING_PROFILES_ACTIVE=prod`) que troca o H2 por Postgres de verdade, lendo `PGHOST`/`PGPORT`/`PGDATABASE`/`PGUSER`/`PGPASSWORD` — o padrão de variáveis que provedores como Railway já injetam automaticamente ao conectar um banco Postgres ao serviço — e desliga o console do H2. Em produção, defina também `JWT_SECRET` e `INSTAGRAM_TOKEN_KEY` com valores fortes (os defaults no `application.yml` são só pra dev local). `server.port` lê a env var `PORT` quando presente (padrão que a maioria dos PaaS usa), com fallback pra `8080`.

### Convenção de tratamento de erros

Violações de regra de negócio devem lançar `IllegalStateException` (transição de estado inválida, permissão negada) ou `IllegalArgumentException` (entidade não encontrada, input inválido) a partir dos services — ambas são capturadas centralmente pelo `ApiExceptionHandler` e retornadas como `400` com `{"erro": "..."}`. Falhas de Bean Validation em DTOs `@Valid @RequestBody` são capturadas separadamente e retornadas como `{"erros": {campo: mensagem}}`.
