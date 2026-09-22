# CLAUDE.md

Este arquivo fornece orientações ao Claude Code (claude.ai/code) ao trabalhar com código neste repositório.

## Visão geral do projeto

API de Gestão e Controle de Projetos da Pascom Santuário N. Sra. de Fátima — uma API REST em Spring Boot 3.2 / Java 17 para gerenciar o fluxo de conteúdo de uma equipe de comunicação paroquial: ideias de conteúdo, um quadro de produção estilo Kanban ("cartões") e escala de voluntários para eventos.

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
- `config/` — `WebConfig` (CORS global em `/api/**`, atualmente `allowedOriginPatterns("*")`), `SecurityConfig`/`SecurityFilter` (autenticação stateless via JWT, ver abaixo) e `ApiExceptionHandler` (`@RestControllerAdvice` que traduz `IllegalStateException`/`IllegalArgumentException`/erros de validação/`AuthenticationException` em corpos JSON `400`/`401`).

### Autenticação

A API usa **JWT stateless** via Spring Security. `POST /api/usuarios/login` autentica com `AuthenticationManager` e devolve um `LoginResponseDTO{token, usuario}`; o front deve enviar esse token em todas as demais chamadas via header `Authorization: Bearer <token>`. `SecurityFilter` ([SecurityFilter.java](src/main/java/br/org/pascom/config/SecurityFilter.java)) lê e valida o token em cada request e popula o `SecurityContext`; `TokenService` ([TokenService.java](src/main/java/br/org/pascom/service/TokenService.java)) gera/valida o JWT (segredo em `api.security.token.secret`, sobrescrevível pela env var `JWT_SECRET` — **troque o valor padrão antes de produção**). Apenas `POST /api/usuarios/cadastro` e `POST /api/usuarios/login` são públicos; todo o resto de `/api/**` exige token válido. Nos controllers, o usuário autenticado é injetado via `@AuthenticationPrincipal Usuario` — os métodos de service que representam "quem está fazendo a ação" (`moverEtapa`, `adicionarComentario`, `alterarRole`, `inscreverVoluntario`, criar/votar `Ideia`) recebem esse `Usuario` diretamente, e não mais um ID enviado pelo cliente. Respostas de erro de autenticação (401) e autorização (403) seguem o mesmo formato `{"erro": "..."}` do restante da API, configurado em `SecurityConfig.exceptionHandling`.

### Modelo de domínio

- **Usuario**: um membro da equipe; implementa `UserDetails` do Spring Security (o `Role` vira a authority `ROLE_<role>`). `Role` pode ser `COORDENACAO` (coordenação/admin), `ASSESSOR` ou `VOLUNTARIO`. O primeiro usuário jamais cadastrado é automaticamente promovido a `COORDENACAO`; todos os demais começam como `VOLUNTARIO`. Só `COORDENACAO` pode alterar a função de outro usuário ([UsuarioService.java](src/main/java/br/org/pascom/service/UsuarioService.java)). Senhas são hasheadas com BCrypt e marcadas com `@JsonIgnore`.
- **Ideia**: uma ideia de conteúdo. Qualquer um pode criar e votar (alternar voto) em ideias. Uma ideia pode ser "adotada" (`adotada = true`), o que a converte 1:1 em um novo `Cartao` na etapa `IDEIA` via `IdeiaService.transformarEmCartao`.
- **Cartao**: um cartão de produção que percorre o pipeline de `Etapa`: `IDEIA -> ROTEIRO -> GRAVACAO -> EDICAO -> REVISAO -> AGENDADO -> PUBLICADO`. Avançar para `AGENDADO` ou `PUBLICADO` é bloqueado em `CartaoService.moverEtapa`: o usuário solicitante não pode ser `VOLUNTARIO`, e o `Checklist` embutido no cartão (5 flags booleanas de segurança/revisão) precisa estar totalmente completo (`Checklist.isCompleto()`). Essa é a principal regra de negócio do código — respeite-a ao mexer em transições de etapa do cartão.
- **Evento** / **SlotEscala**: um evento com uma lista de "slots" de voluntários (`SlotTipo`: `FOTO`, `VIDEO`, `STORIES_AO_VIVO`, `TRANSMISSAO`). Só `COORDENACAO` pode criar eventos/marcar datas no calendário (`EventoService.criar`); voluntários (qualquer papel) se auto-inscrevem em um slot específico (`EventoService.inscreverVoluntario`, bloqueado se já estiverem inscritos em qualquer slot daquele evento) ou se desinscrevem (`desinscreverVoluntario`).
- **Comentario**: comentários em thread anexados a um `Cartao`, de autoria de um `Usuario`.

### Persistência

Banco H2 baseado em arquivo em `./data/pascomdb` (ignorado pelo git) com `ddl-auto: update` — o schema evolui automaticamente a partir das entidades, sem scripts de migração (sem Flyway/Liquibase). `postgresql` é uma dependência runtime declarada mas não configurada atualmente em `application.yml` (quem roda de fato é o H2). O SQL é logado (`show-sql: true`).

### Convenção de tratamento de erros

Violações de regra de negócio devem lançar `IllegalStateException` (transição de estado inválida, permissão negada) ou `IllegalArgumentException` (entidade não encontrada, input inválido) a partir dos services — ambas são capturadas centralmente pelo `ApiExceptionHandler` e retornadas como `400` com `{"erro": "..."}`. Falhas de Bean Validation em DTOs `@Valid @RequestBody` são capturadas separadamente e retornadas como `{"erros": {campo: mensagem}}`.
