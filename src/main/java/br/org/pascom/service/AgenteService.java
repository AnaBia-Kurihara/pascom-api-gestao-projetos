package br.org.pascom.service;

import br.org.pascom.dto.AgenteMensagemDTO;
import br.org.pascom.dto.AgenteRespostaDTO;
import br.org.pascom.dto.AgenteTurnoDTO;
import br.org.pascom.dto.EventoDTO;
import br.org.pascom.dto.EventoRequestDTO;
import br.org.pascom.dto.IdeiaRequestDTO;
import br.org.pascom.dto.IdeiaResponseDTO;
import br.org.pascom.model.Usuario;
import br.org.pascom.model.enums.Setor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assistente de IA da equipe: conversa em linguagem natural e executa ações reais no sistema
 * (criar evento, salvar ideia, consultar calendário/ideias) via "function calling" do Gemini.
 * Cada chamada HTTP resolve uma mensagem inteira, incluindo as idas e vindas com o modelo até
 * ele decidir que já pode responder em texto puro (sem mais ferramentas pra chamar).
 */
@Service
public class AgenteService {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={key}";
    private static final int MAX_RODADAS_FERRAMENTA = 6;

    @Value("${api.gemini.chave}")
    private String apiKey;

    private final EventoService eventoService;
    private final IdeiaService ideiaService;
    private final RestClient restClient = RestClient.create();

    public AgenteService(EventoService eventoService, IdeiaService ideiaService) {
        this.eventoService = eventoService;
        this.ideiaService = ideiaService;
    }

    public AgenteRespostaDTO conversar(AgenteMensagemDTO dados, Usuario solicitante) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("O assistente ainda não foi configurado. Fale com quem administra o sistema.");
        }

        Setor setorAtual = dados.setor() != null ? dados.setor() : Setor.REDES_SOCIAIS;

        List<Map<String, Object>> contents = new ArrayList<>();
        if (dados.historico() != null) {
            for (AgenteTurnoDTO turno : dados.historico()) {
                contents.add(Map.of(
                        "role", "agente".equals(turno.papel()) ? "model" : "user",
                        "parts", List.of(Map.of("text", turno.texto()))
                ));
            }
        }
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", dados.mensagem()))));

        List<String> acoes = new ArrayList<>();
        String textoFinal = null;

        for (int rodada = 0; rodada < MAX_RODADAS_FERRAMENTA && textoFinal == null; rodada++) {
            Map<String, Object> resposta = chamarGemini(contents, solicitante, setorAtual);
            Map<String, Object> chamada = extrairFunctionCall(resposta);

            if (chamada == null) {
                textoFinal = extrairTexto(resposta);
                break;
            }

            String nome = (String) chamada.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) chamada.getOrDefault("args", Map.of());

            contents.add(Map.of("role", "model", "parts", List.of(Map.of("functionCall", chamada))));

            Map<String, Object> resultado = executarFerramenta(nome, args, solicitante, setorAtual, acoes);

            contents.add(Map.of("role", "user", "parts", List.of(Map.of(
                    "functionResponse", Map.of("name", nome, "response", resultado)
            ))));
        }

        if (textoFinal == null || textoFinal.isBlank()) {
            textoFinal = acoes.isEmpty()
                    ? "Não consegui entender esse pedido. Pode tentar de outro jeito?"
                    : "Pronto — já fiz o que você pediu.";
        }

        return new AgenteRespostaDTO(textoFinal, acoes);
    }

    private Map<String, Object> chamarGemini(List<Map<String, Object>> contents, Usuario solicitante, Setor setorAtual) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("system_instruction", Map.of("parts", List.of(Map.of("text", promptSistema(solicitante, setorAtual)))));
        corpo.put("contents", contents);
        corpo.put("tools", List.of(Map.of("functionDeclarations", ferramentas())));

        try {
            Map<?, ?> resp = restClient.post()
                    .uri(GEMINI_URL, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .body(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> tipado = (Map<String, Object>) resp;
            return tipado;
        } catch (RestClientException e) {
            throw new IllegalStateException("Não foi possível falar com o assistente agora. Tente de novo em instantes.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extrairFunctionCall(Map<String, Object> resposta) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) resposta.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            for (Map<String, Object> parte : parts) {
                if (parte.containsKey("functionCall")) {
                    return (Map<String, Object>) parte.get("functionCall");
                }
            }
        } catch (Exception ignorada) {
            // resposta em formato inesperado — trata como "sem chamada de ferramenta"
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extrairTexto(Map<String, Object> resposta) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) resposta.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> parte : parts) {
                Object texto = parte.get("text");
                if (texto != null) sb.append(texto);
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> executarFerramenta(String nome, Map<String, Object> args, Usuario solicitante, Setor setorAtual, List<String> acoes) {
        try {
            return switch (nome) {
                case "criar_evento" -> criarEvento(args, solicitante, acoes);
                case "criar_ideia" -> criarIdeia(args, solicitante, setorAtual, acoes);
                case "listar_eventos" -> listarEventos();
                case "listar_ideias" -> listarIdeias(setorAtual);
                default -> Map.of("erro", "Ferramenta desconhecida: " + nome);
            };
        } catch (IllegalStateException | IllegalArgumentException e) {
            return Map.of("erro", e.getMessage());
        } catch (Exception e) {
            return Map.of("erro", "Não consegui completar essa ação agora.");
        }
    }

    private Map<String, Object> criarEvento(Map<String, Object> args, Usuario solicitante, List<String> acoes) {
        String titulo = String.valueOf(args.get("titulo"));
        LocalDate data = LocalDate.parse(String.valueOf(args.get("data")));
        LocalTime horario = LocalTime.parse(String.valueOf(args.get("horario")));
        String local = String.valueOf(args.getOrDefault("local", "Igreja Matriz"));
        Boolean grande = Boolean.TRUE.equals(args.get("eventoGrande"));

        EventoRequestDTO req = new EventoRequestDTO(titulo, data, horario, local, grande, null);
        EventoDTO criado = eventoService.criar(req, solicitante);
        acoes.add("Evento criado: \"" + titulo + "\" em " + data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " às " + horario);
        return Map.of("sucesso", true, "eventoId", criado.id());
    }

    private Map<String, Object> criarIdeia(Map<String, Object> args, Usuario solicitante, Setor setorAtual, List<String> acoes) {
        String titulo = String.valueOf(args.get("titulo"));
        String tema = String.valueOf(args.getOrDefault("tema", "Geral"));
        String descricao = args.get("descricao") != null ? String.valueOf(args.get("descricao")) : null;

        IdeiaRequestDTO req = new IdeiaRequestDTO(titulo, descricao, tema, setorAtual);
        ideiaService.criar(req, solicitante);
        acoes.add("Ideia adicionada ao banco de ideias: \"" + titulo + "\"");
        return Map.of("sucesso", true);
    }

    private Map<String, Object> listarEventos() {
        LocalDate hoje = LocalDate.now();
        List<String> resumo = eventoService.listarTodos().stream()
                .filter(e -> !e.data().isBefore(hoje))
                .sorted(Comparator.comparing(EventoDTO::data))
                .map(e -> e.data() + " " + e.horario() + " - " + e.titulo() + " (" + e.local() + ")")
                .limit(50)
                .toList();
        return Map.of("eventos", resumo);
    }

    private Map<String, Object> listarIdeias(Setor setorAtual) {
        List<String> resumo = ideiaService.listarPorSetor(setorAtual).stream()
                .map(i -> i.titulo() + " (tema: " + i.tema() + (Boolean.TRUE.equals(i.adotada()) ? ", já virou cartão" : "") + ")")
                .limit(50)
                .toList();
        return Map.of("ideias", resumo);
    }

    private String promptSistema(Usuario solicitante, Setor setorAtual) {
        return "Você é o assistente de IA da equipe da Pascom Fátima (comunicação do Santuário Nossa Senhora de Fátima, "
                + "Santo Amaro). Hoje é " + LocalDate.now() + ". Fale português do Brasil, direto e simpático, sem enrolação. "
                + "Quem está te chamando é " + solicitante.getNome() + " (papel: " + solicitante.getRole() + "), e a conversa "
                + "está acontecendo dentro do espaço do setor " + setorAtual + " — toda ideia que você salvar (criar_ideia) "
                + "entra automaticamente nesse setor, não precisa perguntar qual setor usar. "
                + "Você pode criar eventos no calendário (ferramenta criar_evento) — eventos são compartilhados entre todos "
                + "os setores, mas só o Coordenador Geral tem permissão de criar; se a ferramenta devolver um erro de "
                + "permissão, explique isso com gentileza e não insista. Você pode sugerir e salvar ideias de conteúdo "
                + "(criar_ideia) — isso qualquer pessoa pode fazer. Antes de criar várias coisas de uma vez (ex.: várias "
                + "datas de santos do mês), use listar_eventos pra conferir o que já existe e não duplicar. Quando o pedido "
                + "for algo como 'adicione os santos desse mês no calendário', use seu próprio conhecimento do calendário "
                + "litúrgico católico pra escolher as datas mais conhecidas (não precisa ser exaustivo com santos obscuros) "
                + "e crie um evento por data, com horário e local razoáveis quando não especificados (ex.: 19:00, 'Igreja "
                + "Matriz'). Se pedirem uma ideia de vídeo/post e você não tiver certeza se deve salvar, pode sugerir em "
                + "texto e perguntar se quer que salve no banco de ideias. Seja concisa nas respostas finais, resumindo o "
                + "que foi feito.";
    }

    private List<Map<String, Object>> ferramentas() {
        return List.of(
                Map.of(
                        "name", "criar_evento",
                        "description", "Cria um evento no calendário do santuário, com data e horário.",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "titulo", Map.of("type", "STRING", "description", "Título do evento"),
                                        "data", Map.of("type", "STRING", "description", "Data no formato AAAA-MM-DD"),
                                        "horario", Map.of("type", "STRING", "description", "Horário no formato HH:mm (24h)"),
                                        "local", Map.of("type", "STRING", "description", "Local do evento, ex.: Igreja Matriz"),
                                        "eventoGrande", Map.of("type", "BOOLEAN", "description", "true se for um evento grande/especial")
                                ),
                                "required", List.of("titulo", "data", "horario", "local")
                        )
                ),
                Map.of(
                        "name", "criar_ideia",
                        "description", "Adiciona uma ideia de conteúdo ao banco de ideias da equipe.",
                        "parameters", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "titulo", Map.of("type", "STRING", "description", "Título curto da ideia"),
                                        "tema", Map.of("type", "STRING", "description", "Tema/categoria da ideia"),
                                        "descricao", Map.of("type", "STRING", "description", "Descrição mais detalhada (opcional)")
                                ),
                                "required", List.of("titulo", "tema")
                        )
                ),
                Map.of(
                        "name", "listar_eventos",
                        "description", "Lista os eventos futuros já cadastrados no calendário, pra conferir antes de criar novos.",
                        "parameters", Map.of("type", "OBJECT", "properties", Map.of())
                ),
                Map.of(
                        "name", "listar_ideias",
                        "description", "Lista as ideias de conteúdo já cadastradas, pra não repetir sugestões.",
                        "parameters", Map.of("type", "OBJECT", "properties", Map.of())
                )
        );
    }
}
