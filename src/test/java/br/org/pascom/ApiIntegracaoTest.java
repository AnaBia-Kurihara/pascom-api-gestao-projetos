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
