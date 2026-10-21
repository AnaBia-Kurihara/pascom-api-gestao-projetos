# Pascom Fátima — Sistema de Gestão de Voluntários (VMS)

![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Flyway-4169E1?logo=postgresql&logoColor=white)
![PWA](https://img.shields.io/badge/PWA-Web%20Push-5A0FC8?logo=pwa&logoColor=white)
![Testes](https://img.shields.io/badge/testes-47%20passando-2C7A57)
![Custo](https://img.shields.io/badge/hospedagem-%3C%20US%245%2Fm%C3%AAs-0F2456)

**Do papel ao app:** plataforma web que organiza a Pastoral da Comunicação (Pascom) do Santuário Nossa Senhora de Fátima (Santo Amaro, SP). São mais de 30 voluntários em 5 setores, cuidando de quem fotografa, filma, transmite, projeta e monta os slides de cada missa.

Antes, a escala era montada no papel, em planilha e em mensagens soltas de WhatsApp. Hoje a coordenação monta o mês com poucos toques, e cada voluntário recebe o lembrete no celular. Tudo roda num único serviço, por menos de **US$ 5 por mês**.

> 🔒 **Sistema de uso exclusivo da Pascom.** Só voluntários da pastoral têm acesso. Este repositório está público **apenas como portfólio**: o código não é aberto para uso, cópia ou modificação. Veja [Uso restrito e direitos autorais](#uso-restrito-e-direitos-autorais).

> O sistema tomou forma em cerca de 2 meses, com **IA como parceira de programação**. As versões foram entregues a partir do feedback contínuo da equipe, com o app em uso real.

---

## Sumário
- [O que o sistema faz](#o-que-o-sistema-faz)
- [Desafios técnicos resolvidos](#desafios-técnicos-resolvidos)
- [Arquitetura](#arquitetura)
- [Tecnologias](#tecnologias)
- [Qualidade e testes](#qualidade-e-testes)
- [Rotinas automáticas](#rotinas-automáticas)
- [Evolução guiada por feedback](#evolução-guiada-por-feedback)
- [Uso restrito e direitos autorais](#uso-restrito-e-direitos-autorais)

---

## O que o sistema faz

### 🗓️ Escalas (o coração do sistema)
| Funcionalidade | Como funciona |
|---|---|
| **Geração automática** | No dia 1º, as missas fixas do mês atual e do próximo nascem sozinhas, já com as funções padrão de cada setor (sábado 16h30; domingo 8h, 10h e 18h30). |
| **Candidatura + sigilo** | O voluntário toca em **"Quero servir"**. A coordenação escala em sigilo: até a publicação, **o servidor esconde quem foi escalado** de quem não coordena, inclusive da própria pessoa. |
| **Sugestão automática** | Um botão preenche as vagas abertas em rodízio, só com quem se candidatou. Ninguém fica em duas funções na mesma missa, e tem prioridade quem serviu menos. A coordenação revisa e aplica. |
| **Relatório de participação** | Mostra quem serve mais, quem serve menos e quem está há mais tempo parado, pra equilibrar a escala. |
| **Ciclo mensal** | Lembrete pra publicar a partir do dia 25, publicação automática no dia 1º e fechamento do mês que chegou. |
| **Histórico** | Toda mudança fica registrada: quem escalou, trocou, tirou ou publicou. |
| **PDF** | Escala completa no formato de grade impressa da Pascom, em A4 paisagem (OpenPDF). |

### 📲 App instalável e notificações
- **PWA**: instala na tela inicial do Android, do iPhone e do computador.
- **Web Push**: avisos de escalação, lembrete **48h antes de servir**, mudanças de última hora e aniversários, mesmo com o app fechado.
- **Sininho** no app com o histórico de avisos.

### 🎵 Setor Holyrics (montagem dos slides das 4 missas do fim de semana)
- **Liturgia automática do domingo**, lida do site da Paulus (sem API: sitemap + parsing de HTML), com reserva numa API aberta.
- **Links sem login** (token secreto por fim de semana) pros músicos mandarem o repertório e pras outras pastorais mandarem os avisos.
- Textos prontos pra colar em slides, lembretes gentis e alerta quando algo muda depois da montagem.

### 📣 Conteúdo e comunicação
- **Quadro Kanban** de produção (`Ideia → Roteiro → Gravação → Edição → Revisão → Agendado → Publicado`), com checklist de revisão que trava o agendamento.
- **Banco de ideias** com votação e **calendário editorial**.
- **Instagram Graph API**: leitura automática dos posts, histórico de métricas e metas de crescimento.
- **Assistente de IA** (Gemini com *function calling*), que cria eventos e ideias de verdade conversando em português.

### 👥 Equipe
- Login por e-mail/senha ou **Google**, perfis com foto, setores extras e aniversário.
- **Post de aniversário gerado no navegador** (Canvas, 1080×1350) no modelo visual da Pascom.
- Papéis: **Coordenação Geral**, **Coordenação de setor**, **Gestor(a) de escalas** e **Voluntário(a)**.
- **Lixeira** com motivo de exclusão e restauração.

---

## Desafios técnicos resolvidos

| Problema | Solução |
|---|---|
| **40 pessoas tocando "Eu vou" na mesma vaga ao mesmo tempo**: o padrão "conferir e depois gravar" deixava todas receberem sucesso | `UPDATE ... WHERE voluntario IS NULL` atômico. Só uma ganha, e as outras recebem "alguém acabou de pegar essa vaga". Coberto por um teste com 8 threads concorrentes. |
| **Notificações no celular sem pesar na memória** | Web Push implementado **só com o JDK**: criptografia RFC 8291 (ECDH P-256 + HKDF + AES-128-GCM) e assinatura VAPID RFC 8292 (ES256). Validado **byte a byte com o exemplo oficial da RFC** e testado de ponta a ponta no Chrome e no iPhone. |
| **Caber no plano de US$ 5** | Ajuste da JVM (`-Xmx200m`, SerialGC, menos threads no Tomcat): de ~600 MB para ~250 MB, testado gerando 30 PDFs em paralelo. |
| **Sigilo da escala** | A regra vive no servidor (`SlotEscalaDTO.oculto`), e não no front: nenhuma chamada direta à API revela a escala em montagem. |
| **Servidor em UTC, equipe em Brasília** | Fuso padrão `America/Sao_Paulo` na JVM. Antes, os avisos apareciam 3h adiantados e o assistente "achava" que já era amanhã depois das 21h. |
| **Site sem API (liturgia da Paulus)** | Descoberta da página do dia pelo sitemap, casando pelo número do dia no link (a data do sitemap às vezes vem trocada), e parsing com jsoup. Se o layout mudar, o sistema cai sozinho na fonte reserva. |
| **E-mail no Railway (SMTP bloqueado)** | Envio por APIs HTTPS: **Gmail API** com OAuth (só o escopo `gmail.send`, refresh token criptografado) e Resend como reserva. |
| **Segurança de rotas abertas** | Limite de requisições por IP, bloqueio por e-mail após 8 senhas erradas, corpo limitado a 2 MB, `X-Frame-Options`, endpoints de push restritos aos serviços conhecidos (evita SSRF) e tokens secretos (Instagram, Gmail, VAPID) criptografados com AES-GCM. |
| **Front novo depois do deploy** | ETag pelo conteúdo, porque o build grava datas fixas nos arquivos e o `Last-Modified` sempre batia. Junto, um aviso de "Saiu uma atualização" consultando `/api/versao`. |
| **Mudanças de schema sem derrubar produção** | Flyway com baseline numa base que já existia, migrações aditivas e versões fixadas (Flyway 11 com Spring Boot 3.2 e PostgreSQL 18). |

---

## Arquitetura

```
┌────────────────────────────┐        ┌──────────────────────────────────────────┐
│  Front-end (PWA)           │  JWT   │  Spring Boot (um serviço só)             │
│  index.html · sw.js        │ ─────▶ │  controller → service → repository       │
│  HTML/CSS/JS puros         │ /api   │  Spring Security · @Scheduled · Web Push │
└────────────────────────────┘        └───────────────┬──────────────────────────┘
        ▲                                             │
        │ notificação                                 ▼
 ┌──────┴───────────────┐              ┌──────────────────────────┐
 │ FCM / Apple / Mozilla │◀── push ────│ PostgreSQL (Flyway)       │
 └───────────────────────┘             └──────────────────────────┘
        Integrações: Gmail API · Instagram Graph API · Gemini · Google Login · Paulus (HTML)
```

- **Camadas**: `controller/` (HTTP ↔ DTO), `service/` (regras de negócio), `repository/` (Spring Data JPA), `model/` (entidades), `dto/` (records com Bean Validation) e `config/` (segurança, filtros, agendamentos).
- **Front-end sem build**: um único `index.html`, servido pelo próprio Spring Boot na raiz. As chamadas usam caminho relativo (`/api`), então o mesmo código funciona no ambiente local e em produção.
- **Erros padronizados**: `{"erro": "..."}` para regra de negócio e `{"erros": {campo: msg}}` para validação.

---

## Tecnologias

| Camada | Tecnologias |
|---|---|
| **Backend** | Java 17, Spring Boot 3.2 (Web, Data JPA, Validation, Security), JWT (jjwt 0.12), Hibernate 6, Lombok, Maven |
| **Banco** | PostgreSQL + Flyway 11 em produção · H2 local |
| **Front-end** | HTML, CSS e JavaScript puros, PWA (manifest + service worker), Canvas, tema claro/escuro, mobile-first |
| **Bibliotecas** | OpenPDF (PDF da escala), jsoup (leitura de HTML) |
| **Integrações** | Gmail API, Instagram Graph API, Google Gemini (function calling), Google Identity Services, Resend, Web Push (FCM, Apple, Mozilla) |
| **Testes** | JUnit 5, Spring Boot Test, MockMvc, AssertJ |
| **Infra** | Railway (app + Postgres), Railway CLI |

---

## Qualidade e testes

**47 testes automáticos**, rodados antes de cada atualização em produção:

| Classe | Cobre |
|---|---|
| `ApiIntegracaoTest` | O sistema inteiro pela API, com H2 em memória e sem internet: cadastro e login, permissões, **sigilo da escala até publicar**, **8 pessoas disputando a mesma vaga**, sugestão de escala em rodízio, relatório de participação, links sem login do Holyrics, limites de tentativas (429/413), notificações e recuperação de senha. |
| `WebPushCriptoTest` | Criptografia das notificações **idêntica ao exemplo oficial da RFC 8291**, ida e volta e assinatura VAPID. |
| `PaulusLiturgiaServiceTest` | Leitura da liturgia em páginas-modelo com a estrutura real do site (texto inventado). |
| `GmailServiceTest` | Formato do e-mail em UTF-8 e proteção contra injeção de cabeçalho. |

Os testes rodam num ambiente isolado, sem agendamentos e sem acesso à internet: nenhum teste manda e-mail, notificação ou chamada para fora.

---

## Rotinas automáticas

| Quando | O que roda |
|---|---|
| A cada 15 min | Lembrete (app + e-mail + celular) pra quem serve nas próximas 48h |
| Todo dia, 7h | Avisos de aniversário e convite pra coordenação gerar o post |
| Quinta e sexta, 10h | Lembretes gentis do Holyrics (músicas e montagem) |
| Dia 1º, 6h | Gera as missas do mês, publica as escalas pendentes e fecha o mês que chegou |
| A cada 6h | Sincroniza posts e métricas do Instagram e busca antes a liturgia dos próximos domingos |
| Segunda, 4h | Renova o token do Instagram (que vence em 60 dias) |

---

## Evolução guiada por feedback

O sistema foi para produção cedo e evoluiu em ciclos curtos, a partir do que a equipe pedia:

| Feedback da equipe | Entrega |
|---|---|
| "Só a coordenação deveria ver a escala antes de fechar" | Montagem em sigilo + publicação por mês e setor |
| "A missa de sábado mudou de horário" | Migração automática dos eventos futuros, sem perder quem já estava escalado |
| "A gente esquece que vai servir" | Lembrete de 48h e notificações no celular |
| "Queremos a liturgia da Paulus, que é a mais confiável" | Leitura automática do site da Paulus |
| "Controlo à mão quem serve mais e quem serve menos" | Relatório de participação |
| "Montar o mês leva muito tempo" | Sugestão automática de escala |
| "Pedi nova senha e não chegou" | Envio pelo Gmail da Pascom + aviso à coordenação como reserva |

---

## Uso restrito e direitos autorais

© 2026 Ana Beatriz de Oliveira Kurihara. **Todos os direitos reservados.**

- Este é um sistema **interno e exclusivo** da Pascom do Santuário Nossa Senhora de Fátima. O acesso é **somente para voluntários da pastoral**.
- O código está visível **apenas para fins de portfólio**. **Não é permitido** usar, copiar, modificar, hospedar ou distribuir este software, no todo ou em parte, sem autorização por escrito da autora.
- Não há licença de código aberto: sem uma licença, a lei garante todos os direitos à autora (Lei nº 9.610/98 e Lei nº 9.609/98).
- Contribuições externas (*pull requests*) não são aceitas.

Interesse no projeto ou em algo parecido para a sua comunidade? Entre em contato pelo [LinkedIn](https://www.linkedin.com/).

---

Feito com 💙 para a Pascom do Santuário Nossa Senhora de Fátima.
