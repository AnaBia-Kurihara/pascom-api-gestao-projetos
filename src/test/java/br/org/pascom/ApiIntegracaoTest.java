package br.org.pascom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * O sistema inteiro rodando (banco H2 em memória, perfil "test"), chamado pela API como o front chama:
 * cadastro e login, permissões, sigilo da escala em montagem, links sem login do Holyrics e a vaga
 * disputada por várias pessoas ao mesmo tempo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIntegracaoTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private br.org.pascom.repository.EventoRepository eventoRepository;
    @Autowired private br.org.pascom.repository.UsuarioRepository usuarioRepository;

    /** Só pode existir um Coordenador Geral no sistema: é criado uma vez e usado em todos os testes. */
    private String geral;

    @BeforeAll
    void criarCoordenacaoGeral() throws Exception {
        geral = cadastrarELogar("COORDENADOR_GERAL", null).token;
    }

    // ---------------------------------------------------------------- cadastro, login e acesso

    @Test
    void apiSemLoginResponde401ComMensagem() throws Exception {
        MvcResult r = mvc.perform(get("/api/eventos")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(corpo(r).has("erro")).isTrue();
    }

    @Test
    void paginaInicialEVersaoSaoPublicas() throws Exception {
        assertThat(mvc.perform(get("/")).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/api/versao")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void emailComMaiusculasEEspacosEntraDoMesmoJeito() throws Exception {
        String email = "Pessoa." + UUID.randomUUID().toString().substring(0, 8) + "@Teste.com";
        assertThat(cadastrar(email, "VOLUNTARIO", "REDES_SOCIAIS").getResponse().getStatus()).isEqualTo(201);

        // login digitado de outro jeito (maiúsculas diferentes e espaço sobrando) entra na mesma conta
        MvcResult r = enviar(post("/api/usuarios/login"), null, Map.of("email", "  " + email.toUpperCase() + " ", "senha", "senha123"));
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpo(r).path("token").asText()).isNotBlank();
        assertThat(corpo(r).path("usuario").path("email").asText()).isEqualTo(email.toLowerCase());
    }

    @Test
    void senhaErradaNaoEntra() throws Exception {
        Pessoa p = cadastrarELogar("VOLUNTARIO", "JOVENS");
        MvcResult r = enviar(post("/api/usuarios/login"), null, Map.of("email", p.email, "senha", "errada!!"));
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void segundoCoordenadorGeralEhBloqueado() throws Exception {
        MvcResult r = cadastrar(emailNovo(), "COORDENADOR_GERAL", null);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(corpo(r).path("erro").asText()).isNotBlank();
    }

    @Test
    void voluntarioPrecisaDeSetor() throws Exception {
        assertThat(cadastrar(emailNovo(), "VOLUNTARIO", null).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void cadastroIncompletoMostraErroPorCampo() throws Exception {
        MvcResult r = enviar(post("/api/usuarios/cadastro"), null, Map.of("nome", "Sem e-mail", "senha", "1"));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(corpo(r).path("erros").has("email")).isTrue();
    }

    @Test
    void senhaErradaMuitasVezesBloqueiaAqueleEmail() throws Exception {
        Pessoa p = cadastrarELogar("VOLUNTARIO", "JOVENS");
        for (int i = 0; i < 8; i++) {
            assertThat(enviar(post("/api/usuarios/login"), null, Map.of("email", p.email, "senha", "errada" + i))
                    .getResponse().getStatus()).isEqualTo(401);
        }
        // nem a senha certa entra enquanto estiver bloqueado
        MvcResult r = enviar(post("/api/usuarios/login"), null, Map.of("email", p.email, "senha", "senha123"));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(corpo(r).path("erro").asText()).contains("Espere 15 minutos");
    }

    @Test
    void mesmoEnderecoTentandoLoginSemPararRecebe429() throws Exception {
        int ultimo = 0;
        for (int i = 0; i < 31; i++) {
            ultimo = mvc.perform(post("/api/usuarios/login").header("X-Forwarded-For", "203.0.113.7")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"ninguem" + i + "@pascom.test\",\"senha\":\"x\"}")).andReturn().getResponse().getStatus();
        }
        assertThat(ultimo).isEqualTo(429);
    }

    @Test
    void envioGrandeDemaisEhRecusado() throws Exception {
        byte[] grande = new byte[3 * 1024 * 1024];
        MvcResult r = mvc.perform(post("/api/publico/avisos/qualquer").contentType(MediaType.APPLICATION_JSON).content(grande)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(413);
    }

    @Test
    void sistemaNaoPodeSerColocadoDentroDeOutroSite() throws Exception {
        assertThat(mvc.perform(get("/")).andReturn().getResponse().getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
    }

    @Test
    void relogioDoServidorEstaNoHorarioDeBrasilia() {
        assertThat(java.util.TimeZone.getDefault().getID()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void esqueciSenhaSemEmailAvisaACoordenacaoGeralUmaVezSo() throws Exception {
        Pessoa p = cadastrarELogar("VOLUNTARIO", "DATASHOW");
        // Nos testes não há chave do Resend: é o mesmo caso de produção sem domínio verificado (e-mail não sai).
        for (int i = 0; i < 2; i++) {
            assertThat(enviar(post("/api/usuarios/esqueci-senha"), null, Map.of("email", p.email)).getResponse().getStatus()).isEqualTo(200);
        }
        long avisos = 0;
        for (JsonNode a : corpo(mvc.perform(autenticado(get("/api/avisos"), geral)).andReturn()).path("avisos")) {
            if (("linkSenha:" + p.id).equals(a.path("acao").asText())) avisos++;
        }
        assertThat(avisos).isEqualTo(1);
    }

    @Test
    void esqueciSenhaNaoRevelaSeOEmailExiste() throws Exception {
        MvcResult r = enviar(post("/api/usuarios/esqueci-senha"), null, Map.of("email", emailNovo()));
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- escala: sigilo e vaga disputada

    @Test
    void escalaEmMontagemEscondeQuemFoiEscaladoAtePublicar() throws Exception {
        Pessoa vol = cadastrarELogar("VOLUNTARIO", "REDES_SOCIAIS");
        JsonNode ev = criarEvento("Evento de teste do sigilo", "Foto", "REDES_SOCIAIS");
        long eventoId = ev.path("id").asLong(), slotId = ev.path("slots").get(0).path("id").asLong();

        assertThat(enviar(post("/api/eventos/" + eventoId + "/slots/" + slotId + "/candidatura"), vol.token, null)
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(enviar(post("/api/eventos/" + eventoId + "/slots/" + slotId + "/escalar"), geral, Map.of("usuarioId", vol.id))
                .getResponse().getStatus()).isEqualTo(200);

        // Em montagem: nem a própria pessoa sabe ainda.
        JsonNode slot = slotVistoPor(vol.token, eventoId, slotId);
        assertThat(slot.path("oculto").asBoolean()).isTrue();
        assertThat(slot.path("voluntarioId").isNull()).isTrue();
        assertThat(slot.path("candidatos").isNull()).isTrue(); // candidatos são só da coordenação
        assertThat(slotVistoPor(geral, eventoId, slotId).path("voluntarioId").asLong()).isEqualTo(vol.id);

        String mes = YearMonth.from(LocalDate.parse(ev.path("data").asText())).toString();
        assertThat(mvc.perform(autenticado(post("/api/escalas/publicar").param("mes", mes).param("setor", "REDES_SOCIAIS"), geral))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        slot = slotVistoPor(vol.token, eventoId, slotId);
        assertThat(slot.path("oculto").asBoolean()).isFalse();
        assertThat(slot.path("voluntarioId").asLong()).isEqualTo(vol.id);
    }

    @Test
    void voluntarioNaoEscalaNinguem() throws Exception {
        Pessoa vol = cadastrarELogar("VOLUNTARIO", "REDES_SOCIAIS");
        Pessoa outro = cadastrarELogar("VOLUNTARIO", "REDES_SOCIAIS");
        JsonNode ev = criarEvento("Evento de teste de permissão", "Vídeo", "REDES_SOCIAIS");
        MvcResult r = enviar(post("/api/eventos/" + ev.path("id").asLong() + "/slots/" + ev.path("slots").get(0).path("id").asLong() + "/escalar"),
                vol.token, Map.of("usuarioId", outro.id));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void variasPessoasNaMesmaVagaGeralSoUmaGanha() throws Exception {
        JsonNode ev = criarEvento("Evento de teste da vaga disputada", "Apoio geral", null);
        long eventoId = ev.path("id").asLong(), slotId = ev.path("slots").get(0).path("id").asLong();
        List<Pessoa> pessoas = new ArrayList<>();
        for (int i = 0; i < 8; i++) pessoas.add(cadastrarELogar("VOLUNTARIO", i % 2 == 0 ? "JOVENS" : "DATASHOW"));

        ExecutorService pool = Executors.newFixedThreadPool(pessoas.size());
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> respostas = new ArrayList<>();
        for (Pessoa p : pessoas) {
            respostas.add(pool.submit(() -> {
                largada.await();
                return enviar(post("/api/eventos/" + eventoId + "/slots/" + slotId + "/inscrever"), p.token, null)
                        .getResponse().getStatus();
            }));
        }
        largada.countDown();
        int sucessos = 0;
        for (Future<Integer> f : respostas) if (f.get() == 200) sucessos++;
        pool.shutdown();

        assertThat(sucessos).isEqualTo(1);
        assertThat(slotVistoPor(geral, eventoId, slotId).path("voluntarioId").isNumber()).isTrue();
    }

    // ---------------------------------------------------------------- Holyrics

    @Test
    void montagemDoHolyricsEhSoDaEquipeDoHolyrics() throws Exception {
        Pessoa redes = cadastrarELogar("VOLUNTARIO", "REDES_SOCIAIS");
        Pessoa holy = cadastrarELogar("VOLUNTARIO", "HOLYRICS");

        assertThat(mvc.perform(autenticado(get("/api/holyrics/fins-de-semana"), redes.token)).andReturn()
                .getResponse().getStatus()).isEqualTo(400);
        MvcResult r = mvc.perform(autenticado(get("/api/holyrics/fins-de-semana"), holy.token)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpo(r)).hasSize(6);
    }

    @Test
    void linkDosMusicosFuncionaSemLoginEChegaNaMontagem() throws Exception {
        Pessoa holy = cadastrarELogar("VOLUNTARIO", "HOLYRICS");
        String sabado = proximoSabado().plusWeeks(2).toString();
        JsonNode det = corpo(mvc.perform(autenticado(get("/api/holyrics/montagem").param("sabado", sabado), holy.token)).andReturn());
        // sem internet nos testes: a liturgia falha, mas a tela continua funcionando e explica
        assertThat(det.path("liturgiaErro").asText()).isNotBlank();
        String token = det.path("linkMusicos").asText().replaceAll(".*[?&]t=", "");

        MvcResult pub = mvc.perform(get("/api/publico/musicos/" + token)).andReturn();
        assertThat(pub.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpo(pub).path("missas")).hasSize(4);

        Map<String, Object> envio = Map.of("enviadoPor", "Ministério de teste", "musicas", List.of(
                Map.of("momento", "Entrada", "titulo", "Música de entrada", "letra", ""),
                Map.of("momento", "Comunhão", "titulo", "Música de comunhão", "letra", "Letra de teste")));
        assertThat(enviar(put("/api/publico/musicos/" + token + "/DOM_1000"), null, envio).getResponse().getStatus()).isEqualTo(200);

        det = corpo(mvc.perform(autenticado(get("/api/holyrics/montagem").param("sabado", sabado), holy.token)).andReturn());
        assertThat(det.path("musicasRecebidas").path("DOM_1000").asInt()).isEqualTo(2);
        assertThat(det.path("musicasRecebidas").path("SAB_1630").asInt()).isZero();
    }

    @Test
    void linkInvalidoOuMissaInexistenteDaoErroAmigavel() throws Exception {
        assertThat(mvc.perform(get("/api/publico/musicos/nao-existe")).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(get("/api/publico/avisos/nao-existe")).andReturn().getResponse().getStatus()).isEqualTo(400);

        Pessoa holy = cadastrarELogar("VOLUNTARIO", "HOLYRICS");
        JsonNode det = corpo(mvc.perform(autenticado(get("/api/holyrics/montagem").param("sabado", proximoSabado().toString()), holy.token)).andReturn());
        String token = det.path("linkMusicos").asText().replaceAll(".*[?&]t=", "");
        MvcResult r = enviar(put("/api/publico/musicos/" + token + "/MISSA_FALSA"), null,
                Map.of("enviadoPor", "Alguém", "musicas", List.of(Map.of("titulo", "X"))));
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void avisoDePastoralChegaPeloLinkSemLogin() throws Exception {
        Pessoa holy = cadastrarELogar("VOLUNTARIO", "HOLYRICS");
        String sabado = proximoSabado().plusWeeks(3).toString();
        JsonNode det = corpo(mvc.perform(autenticado(get("/api/holyrics/montagem").param("sabado", sabado), holy.token)).andReturn());
        String token = det.path("linkAvisos").asText().replaceAll(".*[?&]t=", "");

        MvcResult r = enviar(post("/api/publico/avisos/" + token), null,
                Map.of("pastoral", "Pastoral do Batismo", "texto", "Aviso de teste", "missa", ""));
        assertThat(r.getResponse().getStatus()).isEqualTo(200);

        det = corpo(mvc.perform(autenticado(get("/api/holyrics/montagem").param("sabado", sabado), holy.token)).andReturn());
        assertThat(det.path("avisosPastorais")).hasSize(1);
        assertThat(det.path("avisosPastorais").get(0).path("missaRotulo").asText()).isEqualTo("Todas as missas");
    }

    // ---------------------------------------------------------------- relatório de participação

    @Test
    void participacaoMostraQuemServiuQuemTemMarcadoEQuemNuncaServiu() throws Exception {
        Pessoa coord = cadastrarELogar("COORDENADOR", "TRANSMISSOES_VIDEOS");
        Pessoa antigo = cadastrarELogar("VOLUNTARIO", "TRANSMISSOES_VIDEOS");
        Pessoa marcado = cadastrarELogar("VOLUNTARIO", "TRANSMISSOES_VIDEOS");
        Pessoa novato = cadastrarELogar("VOLUNTARIO", "TRANSMISSOES_VIDEOS");

        // Missa que já aconteceu (há 20 dias), com "antigo" escalado. Gravada direto: a API não cria evento no passado.
        LocalDate passado = LocalDate.now(FUSO).minusDays(20);
        br.org.pascom.model.Evento ev = br.org.pascom.model.Evento.builder().titulo("Missa passada de teste")
                .data(passado).horario(java.time.LocalTime.of(10, 0)).local("Santuário").build();
        ev.getSlots().add(br.org.pascom.model.SlotEscala.builder().rotulo("Câmera 1")
                .setor(br.org.pascom.model.enums.Setor.TRANSMISSOES_VIDEOS)
                .voluntario(usuarioRepository.findById(antigo.id).orElseThrow()).evento(ev).build());
        eventoRepository.save(ev);

        // Mês que vem: "marcado" escalado, "novato" só se candidatou.
        JsonNode futuro = criarEvento("Evento de teste da participação", "Câmera 2", "TRANSMISSOES_VIDEOS");
        long eventoId = futuro.path("id").asLong(), slotId = futuro.path("slots").get(0).path("id").asLong();
        assertThat(enviar(post("/api/eventos/" + eventoId + "/slots/" + slotId + "/escalar"), coord.token, Map.of("usuarioId", marcado.id))
                .getResponse().getStatus()).isEqualTo(200);
        JsonNode outro = criarEvento("Outro evento de teste da participação", "Câmera 3", "TRANSMISSOES_VIDEOS");
        assertThat(enviar(post("/api/eventos/" + outro.path("id").asLong() + "/slots/" + outro.path("slots").get(0).path("id").asLong() + "/candidatura"),
                novato.token, null).getResponse().getStatus()).isEqualTo(200);

        MvcResult r = mvc.perform(autenticado(get("/api/escalas/participacao").param("setor", "TRANSMISSOES_VIDEOS").param("meses", "3"), coord.token)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        Map<Long, JsonNode> porId = new java.util.HashMap<>();
        for (JsonNode p : corpo(r).path("pessoas")) porId.put(p.path("id").asLong(), p);

        assertThat(porId.get(antigo.id).path("serviu").asInt()).isEqualTo(1);
        assertThat(porId.get(antigo.id).path("ultimaVez").asText()).isEqualTo(passado.toString());
        assertThat(porId.get(antigo.id).path("diasSemServir").asInt()).isEqualTo(20);
        assertThat(porId.get(marcado.id).path("serviu").asInt()).isZero();
        assertThat(porId.get(marcado.id).path("proximas").asInt()).isEqualTo(1);
        assertThat(porId.get(novato.id).path("ultimaVez").isNull()).isTrue();
        assertThat(porId.get(novato.id).path("candidaturas").asInt()).isEqualTo(1);
        // quem menos serviu vem primeiro
        assertThat(corpo(r).path("pessoas").get(0).path("serviu").asInt()).isZero();

        // 0 = desde o começo: também conta a missa de 20 dias atrás
        JsonNode tudo = corpo(mvc.perform(autenticado(get("/api/escalas/participacao").param("setor", "TRANSMISSOES_VIDEOS").param("meses", "0"), coord.token)).andReturn());
        assertThat(tudo.path("totalServicos").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void participacaoEhSoDaCoordenacaoDoSetor() throws Exception {
        Pessoa vol = cadastrarELogar("VOLUNTARIO", "TRANSMISSOES_VIDEOS");
        Pessoa coordOutro = cadastrarELogar("COORDENADOR", "JOVENS");
        for (Pessoa p : List.of(vol, coordOutro)) {
            assertThat(mvc.perform(autenticado(get("/api/escalas/participacao").param("setor", "TRANSMISSOES_VIDEOS"), p.token))
                    .andReturn().getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(mvc.perform(autenticado(get("/api/escalas/participacao").param("setor", "TRANSMISSOES_VIDEOS"), geral))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- sugestão de escala

    @Test
    void sugestaoUsaSoCandidatosEmRodizioSemDuasFuncoesNaMesmaMissa() throws Exception {
        Pessoa coord = cadastrarELogar("COORDENADOR", "JOVENS");
        Pessoa ana = cadastrarELogar("VOLUNTARIO", "JOVENS");
        Pessoa bia = cadastrarELogar("VOLUNTARIO", "JOVENS");
        cadastrarELogar("VOLUNTARIO", "JOVENS"); // não se candidatou a nada: não pode aparecer na sugestão

        JsonNode e1 = criarEventoComFuncoes("Missa 1 da sugestão", "JOVENS", "Função A", "Função B");
        JsonNode e2 = criarEventoComFuncoes("Missa 2 da sugestão", "JOVENS", "Função C");
        JsonNode e3 = criarEventoComFuncoes("Missa 3 da sugestão", "JOVENS", "Função D");
        long a = slot(e1, "Função A"), b = slot(e1, "Função B"), c = slot(e2, "Função C"), d = slot(e3, "Função D");
        candidatar(ana, e1, a); candidatar(ana, e1, b); candidatar(ana, e2, c); candidatar(ana, e3, d);
        candidatar(bia, e1, b); candidatar(bia, e3, d);

        String mes = YearMonth.from(LocalDate.parse(e1.path("data").asText())).toString();
        MvcResult r = mvc.perform(autenticado(get("/api/escalas/sugestao").param("mes", mes).param("setor", "JOVENS"), coord.token)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        Map<Long, Long> quem = new java.util.HashMap<>();
        List<Map<String, Object>> itens = new ArrayList<>();
        for (JsonNode i : corpo(r).path("itens")) {
            if (i.has("usuarioId")) {
                quem.put(i.path("slotId").asLong(), i.path("usuarioId").asLong());
                itens.add(Map.of("eventoId", i.path("eventoId").asLong(), "slotId", i.path("slotId").asLong(), "usuarioId", i.path("usuarioId").asLong()));
            }
        }
        assertThat(quem.get(a)).isEqualTo(ana.id); // só a Ana podia
        assertThat(quem.get(c)).isEqualTo(ana.id); // só a Ana podia
        assertThat(quem.get(b)).isEqualTo(bia.id); // a Ana já está na Função A da mesma missa
        assertThat(quem.get(d)).isEqualTo(bia.id); // rodízio: a Bia tem menos escalas no mês
        assertThat(quem.values()).allMatch(id -> id == ana.id || id == bia.id);

        // Nada foi gravado só por sugerir.
        assertThat(slotVistoPor(coord.token, e1.path("id").asLong(), a).path("voluntarioId").isNull()).isTrue();

        MvcResult ap = enviar(post("/api/escalas/sugestao/aplicar?setor=JOVENS"), coord.token, Map.of("itens", itens));
        assertThat(ap.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpo(ap).path("escalados").asInt()).isEqualTo(4);
        assertThat(slotVistoPor(coord.token, e1.path("id").asLong(), a).path("voluntarioId").asLong()).isEqualTo(ana.id);
        assertThat(slotVistoPor(coord.token, e1.path("id").asLong(), b).path("voluntarioId").asLong()).isEqualTo(bia.id);

        // Aplicar de novo não sobrescreve quem já está.
        JsonNode denovo = corpo(enviar(post("/api/escalas/sugestao/aplicar?setor=JOVENS"), coord.token, Map.of("itens", itens)));
        assertThat(denovo.path("escalados").asInt()).isZero();
        assertThat(denovo.path("problemas")).hasSize(4);
    }

    @Test
    void sugestaoEhSoDaCoordenacao() throws Exception {
        Pessoa vol = cadastrarELogar("VOLUNTARIO", "JOVENS");
        String mes = YearMonth.now(FUSO).plusMonths(1).toString();
        assertThat(mvc.perform(autenticado(get("/api/escalas/sugestao").param("mes", mes).param("setor", "JOVENS"), vol.token))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(enviar(post("/api/escalas/sugestao/aplicar?setor=JOVENS"), vol.token, Map.of("itens", List.of(Map.of("eventoId", 1, "slotId", 1, "usuarioId", 1))))
                .getResponse().getStatus()).isEqualTo(400);
    }

    private JsonNode criarEventoComFuncoes(String titulo, String setor, String... funcoes) throws Exception {
        LocalDate data = YearMonth.now(FUSO).plusMonths(1).atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
        List<Map<String, Object>> slots = new ArrayList<>();
        for (String f : funcoes) { Map<String, Object> s = new LinkedHashMap<>(); s.put("rotulo", f); s.put("setor", setor); slots.add(s); }
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("titulo", titulo + " " + UUID.randomUUID().toString().substring(0, 6));
        ev.put("data", data.toString());
        ev.put("horario", "19:00");
        ev.put("local", "Santuário");
        ev.put("slots", slots);
        MvcResult r = enviar(post("/api/eventos"), geral, ev);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isIn(200, 201);
        return corpo(r);
    }

    private static long slot(JsonNode evento, String funcao) {
        for (JsonNode s : evento.path("slots")) if (s.path("rotulo").asText().equals(funcao)) return s.path("id").asLong();
        throw new AssertionError("Função não encontrada: " + funcao);
    }

    private void candidatar(Pessoa p, JsonNode evento, long slotId) throws Exception {
        assertThat(enviar(post("/api/eventos/" + evento.path("id").asLong() + "/slots/" + slotId + "/candidatura"), p.token, null)
                .getResponse().getStatus()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- notificações no celular

    @Test
    void aparelhoSeInscreveESaiDasNotificacoes() throws Exception {
        Pessoa p = cadastrarELogar("VOLUNTARIO", "DATASHOW");
        String chave = corpo(mvc.perform(autenticado(get("/api/push/chave"), p.token)).andReturn()).path("chave").asText();
        assertThat(br.org.pascom.service.WebPushCripto.deB64(chave)).hasSize(65); // chave pública P-256 que o navegador usa

        Map<String, Object> inscricao = inscricaoFalsa("https://fcm.googleapis.com/fcm/send/teste-" + UUID.randomUUID());
        MvcResult r = enviar(post("/api/push/inscricao"), p.token, inscricao);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpo(r).path("aparelhos").asInt()).isEqualTo(1);
        // o mesmo aparelho de novo não duplica
        assertThat(corpo(enviar(post("/api/push/inscricao"), p.token, inscricao)).path("aparelhos").asInt()).isEqualTo(1);

        r = enviar(post("/api/push/desinscrever"), p.token, Map.of("endpoint", inscricao.get("endpoint")));
        assertThat(corpo(r).path("aparelhos").asInt()).isZero();
    }

    @Test
    void servidorSoChamaServicosDePushConhecidos() throws Exception {
        Pessoa p = cadastrarELogar("VOLUNTARIO", "DATASHOW");
        for (String endereco : List.of("https://meu-servidor.com/coleta", "http://fcm.googleapis.com/x", "https://169.254.169.254/latest")) {
            assertThat(enviar(post("/api/push/inscricao"), p.token, inscricaoFalsa(endereco)).getResponse().getStatus())
                    .as(endereco).isEqualTo(400);
        }
    }

    @Test
    void testeSemAparelhoExplica() throws Exception {
        Pessoa p = cadastrarELogar("VOLUNTARIO", "DATASHOW");
        MvcResult r = enviar(post("/api/push/teste"), p.token, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(corpo(r).path("erro").asText()).contains("Nenhum aparelho");
    }

    @Test
    void appInstalavelTemManifestEServiceWorker() throws Exception {
        MvcResult m = mvc.perform(get("/manifest.json")).andReturn();
        assertThat(m.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpo(m).path("display").asText()).isEqualTo("standalone");
        for (JsonNode ic : corpo(m).path("icons")) {
            assertThat(mvc.perform(get(ic.path("src").asText())).andReturn().getResponse().getStatus()).as(ic.path("src").asText()).isEqualTo(200);
        }
        MvcResult sw = mvc.perform(get("/sw.js")).andReturn();
        assertThat(sw.getResponse().getStatus()).isEqualTo(200);
        assertThat(sw.getResponse().getContentType()).contains("javascript");
    }

    private static Map<String, Object> inscricaoFalsa(String endpoint) {
        java.security.KeyPair aparelho = br.org.pascom.service.WebPushCripto.novoParDeChaves();
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        return Map.of("endpoint", endpoint, "keys", Map.of(
                "p256dh", br.org.pascom.service.WebPushCripto.b64(br.org.pascom.service.WebPushCripto.publicaBruta((java.security.interfaces.ECPublicKey) aparelho.getPublic())),
                "auth", br.org.pascom.service.WebPushCripto.b64(auth)));
    }

    // ---------------------------------------------------------------- apoio

    private record Pessoa(long id, String email, String token) {}

    private Pessoa cadastrarELogar(String role, String setor) throws Exception {
        String email = emailNovo();
        MvcResult c = cadastrar(email, role, setor);
        assertThat(c.getResponse().getStatus()).as(c.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode login = corpo(enviar(post("/api/usuarios/login"), null, Map.of("email", email, "senha", "senha123")));
        return new Pessoa(login.path("usuario").path("id").asLong(), email, login.path("token").asText());
    }

    private MvcResult cadastrar(String email, String role, String setor) throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("nome", "Pessoa de Teste");
        dados.put("email", email);
        dados.put("senha", "senha123");
        dados.put("role", role);
        dados.put("setor", setor);
        dados.put("aniversarioDia", 15);
        dados.put("aniversarioMes", 8);
        return enviar(post("/api/usuarios/cadastro"), null, dados);
    }

    /** Evento extra numa quarta do mês que vem (mês ainda em montagem), com uma função. */
    private JsonNode criarEvento(String titulo, String funcao, String setor) throws Exception {
        LocalDate data = YearMonth.now(FUSO).plusMonths(1).atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("rotulo", funcao);
        slot.put("setor", setor);
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("titulo", titulo + " " + UUID.randomUUID().toString().substring(0, 6));
        ev.put("data", data.toString());
        ev.put("horario", "19:00");
        ev.put("local", "Santuário");
        ev.put("slots", List.of(slot));
        MvcResult r = enviar(post("/api/eventos"), geral, ev);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isIn(200, 201);
        return corpo(r);
    }

    private JsonNode slotVistoPor(String token, long eventoId, long slotId) throws Exception {
        for (JsonNode e : corpo(mvc.perform(autenticado(get("/api/eventos"), token)).andReturn())) {
            if (e.path("id").asLong() != eventoId) continue;
            for (JsonNode s : e.path("slots")) if (s.path("id").asLong() == slotId) return s;
        }
        throw new AssertionError("Função " + slotId + " não apareceu pra essa pessoa");
    }

    private MvcResult enviar(MockHttpServletRequestBuilder req, String token, Object corpo) throws Exception {
        // cada chamada "vem" de um endereço diferente, pra o limite por endereço (LimiteRequisicoesFilter)
        // não somar as chamadas de todos os testes; os testes do limite usam um endereço fixo
        req.header("X-Forwarded-For", "10.0." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
        if (corpo != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo));
        return mvc.perform(token == null ? req : autenticado(req, token)).andReturn();
    }

    private static MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", "Bearer " + token);
    }

    private JsonNode corpo(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String emailNovo() {
        return "teste-" + UUID.randomUUID().toString().substring(0, 12) + "@pascom.test";
    }

    private static LocalDate proximoSabado() {
        return LocalDate.now(FUSO).with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
    }
}
