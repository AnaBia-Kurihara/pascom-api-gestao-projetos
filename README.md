# Pascom Fátima — Gestão da Comunicação Paroquial

Sistema web para organizar o trabalho da **Pascom do Santuário Nossa Senhora de Fátima (Santo Amaro)**: ideias de conteúdo, produção de posts e vídeos, escalas de voluntários, fluxos próprios do Datashow e das Transmissões, e acompanhamento automático do Instagram do santuário.

**Em produção:** https://pascom-api-gestao-projetos-production.up.railway.app/

Backend e front-end vivem no mesmo repositório e sobem como **um serviço só**: o Spring Boot serve a API em `/api/**` e o front-end estático na raiz (`/`).

---

## Funcionalidades

| Área | O que faz |
|---|---|
| **Espaços por setor** | Quatro espaços isolados — Redes Sociais, Jovens, Datashow e Transmissões/Vídeos — com troca pelo menu lateral (gaveta no celular). |
| **Quadro de produção** | Kanban de cartões `Ideia → Roteiro → Gravação → Edição → Revisão → Agendado → Publicado`, com checklist de segurança que trava o agendamento. |
| **Banco de ideias** | Qualquer pessoa sugere e vota; uma ideia vira cartão com um clique. |
| **Calendário** | Datas editoriais, santos do dia e eventos do santuário. |
| **Escalas** | Eventos com vagas (Foto, Vídeo, Stories ao vivo…) em que os voluntários se inscrevem; domingos do mês gerados automaticamente. |
| **Datashow** | Quadro por missa: aguardando músicas → conferindo momentos → montado → passado, com responsável pela montagem e outro por passar. |
| **Transmissões** | Quadro por transmissão (capa, link, tema litúrgico) + guia rápido de câmeras e de como ir ao vivo. |
| **Dashboard do Instagram** | Leitura automática de todos os posts, métricas semanais/mensais/anuais e metas de crescimento. |
| **Assistente de IA** | Chat em português (Gemini) que cria eventos e ideias de verdade e analisa o Instagram. |
| **Lixeira** | Cartões, ideias e escalas excluídos pedem um motivo e podem ser restaurados. |
| **Contas** | Login por e-mail/senha ou Google, recuperação de senha por e-mail. |

## Papéis e permissões

| Papel | Pode |
|---|---|
| **Coordenador(a) Geral** (único no sistema) | Tudo: criar/excluir escalas, gerar domingos, conectar o Instagram, alterar a função de alguém. |
| **Coordenador(a) de setor** | Trabalhar no seu setor e marcar a *conferência doutrinária* do checklist. |
| **Voluntário(a)** | Criar cartões e ideias, votar, avançar etapas, se inscrever em escalas. |

---

## Tecnologias

### Backend
| Tecnologia | Versão | Uso |
|---|---|---|
| **Java** | 17 | Linguagem |
| **Spring Boot** | 3.2.3 | Framework (Web, Data JPA, Validation, Security) |
| **Spring Security + JWT** (jjwt) | 0.12.6 | Autenticação stateless |
| **Hibernate / JPA** | 6.4 | Persistência |
| **Flyway** | 9.22.3 | Migrações do schema em produção |
| **Lombok** | — | Redução de boilerplate nas entidades |
| **Maven** | — | Build e dependências |

### Banco de dados
| Ambiente | Banco |
|---|---|
| Local / desenvolvimento | **H2** (arquivo em `./data/`) |
| Produção | **PostgreSQL** |

### Front-end
- **HTML, CSS e JavaScript puros** em um único arquivo ([`index.html`](src/main/resources/static/index.html)), sem framework nem etapa de build
- Fontes **Figtree** e **Young Serif** (Google Fonts)
- Tema claro/escuro automático, layout responsivo (menu em gaveta no celular)

### Integrações externas
| Serviço | Para quê |
|---|---|
| **Instagram Graph API** (Instagram Login) | Ler posts e métricas da conta do santuário |
| **Google Gemini** (`gemini-3.6-flash`, function calling) | Assistente de IA |
| **Google Identity Services** | Entrar com Google |
| **Resend** (API HTTPS) | E-mails de recuperação de senha |

### Infraestrutura
- **Railway** — hospedagem do app e do PostgreSQL
- **Railway CLI** — deploy manual (`railway up`)
- **GitHub** — versionamento

---

## Estrutura do projeto

```
src/main/
├── java/br/org/pascom/
│   ├── controller/   # Endpoints REST (/api/**)
│   ├── service/      # Regras de negócio e integrações
│   ├── repository/   # Spring Data JPA
│   ├── model/        # Entidades e enums
│   ├── dto/          # Requests/responses (records)
│   └── config/       # Segurança, CORS, tratamento de erros
└── resources/
    ├── application.yml      # Config (perfil default = H2, perfil prod = PostgreSQL)
    ├── db/migration/        # Migrações Flyway (produção)
    └── static/index.html    # Front-end
```

Guia detalhado de decisões e regras de negócio: [`CLAUDE.md`](CLAUDE.md).

---

## Como rodar localmente

**Pré-requisitos:** JDK 17 e Maven. (Com JDK mais novo, como o 26, o Lombok 1.18.30 não compila — use o 17.)

```bash
mvn spring-boot:run
```

Abra http://localhost:8080. O banco H2 é criado sozinho em `./data/pascomdb` (console em `/h2-console`). Crie uma conta pela tela de cadastro — a primeira pessoa a escolher *Coordenador(a) Geral* ocupa essa vaga.

Sem chaves de integração, o app funciona normalmente; só ficam inativos o assistente de IA, o Instagram e o envio de e-mail.

```bash
mvn clean package   # gera o .jar
```

> Ainda não há testes automatizados (`src/test/java` está vazio).

## Variáveis de ambiente

Os valores padrão do `application.yml` servem **só para desenvolvimento** — em produção defina todos.

| Variável | Para quê |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Ativa PostgreSQL + Flyway |
| `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` | Conexão com o Postgres (o Railway injeta sozinho) |
| `PORT` | Porta do servidor (padrão `8080`) |
| `JWT_SECRET` | Chave de assinatura dos tokens |
| `INSTAGRAM_TOKEN_KEY` | Chave AES-GCM que criptografa o token do Instagram no banco |
| `GOOGLE_CLIENT_ID` | Login com Google |
| `GEMINI_API_KEY` | Assistente de IA |
| `RESEND_API_KEY`, `RESEND_FROM` | Envio de e-mail |
| `APP_BASE_URL` | URL pública, usada no link de redefinição de senha |

## Deploy

Hospedado no Railway. A integração automática com o GitHub está desativada, então o deploy é feito pela CLI, depois de dar `git push`:

```bash
railway up --ci --message "o que mudou"
```

Em produção o schema é gerenciado pelo **Flyway** (baseline na versão 1; novas migrações em `src/main/resources/db/migration/`, a partir de `V2__*.sql`). Veja o README dessa pasta.

---

## API

Todos os endpoints exigem `Authorization: Bearer <token>`, exceto os de autenticação. Erros voltam como `{"erro": "mensagem"}`.

| Recurso | Base | Operações principais |
|---|---|---|
| Usuários / auth | `/api/usuarios` | `cadastro`, `login`, `google`, `esqueci-senha`, `redefinir-senha`, `{id}/role` |
| Cartões | `/api/cartoes` | CRUD, `{id}/etapa`, `{id}/comentarios`, `lixeira`, `{id}/restaurar` |
| Ideias | `/api/ideias` | listar, criar, `{id}/votar`, `{id}/adotar`, lixeira |
| Eventos / escalas | `/api/eventos` | criar, `{id}/slots`, `inscrever`/`desinscrever`, `gerar-escalas-mes`, lixeira |
| Datashow | `/api/datashow/missas` | CRUD, `{id}/etapa` |
| Transmissões | `/api/transmissoes` | CRUD, `{id}/etapa`, `gerar-mes` |
| Instagram | `/api/instagram` | `status`, `conectar`, `sincronizar`, `resumo?periodo=`, `metas` |
| Assistente | `/api/agente` | `mensagem` |

## Rotinas automáticas

| Quando | O que roda |
|---|---|
| A cada 6 horas | Sincroniza os posts e métricas do Instagram |
| Dia 1 de cada mês, 6h | Gera as escalas e as transmissões dos domingos do mês (sem duplicar) |

---

## Pontos de atenção

- Sem testes automatizados ainda.
- A Lixeira cobre Cartão, Ideia e Escala; Datashow e Transmissões ainda excluem em definitivo.
- Não há tela para promover alguém a Coordenador — o endpoint existe (`PATCH /api/usuarios/{id}/role`), mas o front não o usa.
- O plano gratuito do Gemini limita a ~5 requisições por minuto para o app inteiro.
- Os e-mails saem de `onboarding@resend.dev` até um domínio próprio ser verificado no Resend.
