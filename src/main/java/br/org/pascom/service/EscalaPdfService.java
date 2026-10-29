package br.org.pascom.service;

import br.org.pascom.dto.EscalaCompletaDTO;
import br.org.pascom.dto.SlotEscalaDTO;
import br.org.pascom.model.enums.Setor;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Gera o PDF da escala do mês no mesmo formato da planilha que a Pascom montava à mão:
 * <ul>
 *   <li>cada <b>coluna é um dia</b> (sábado e domingo lado a lado, e os dias de evento extra);</li>
 *   <li>cada <b>linha é um setor num horário</b> (Datashow 16h30, 8h, 10h, 18h30; Transmissão 10h...);</li>
 *   <li>na célula, <b>os nomes empilhados</b> de quem serve, sem o nome da função;</li>
 *   <li>o <b>Holyrics</b> ocupa o fim de semana inteiro (sábado + domingo) numa célula só;</li>
 *   <li>azul = o setor não serve naquele dia/horário; amarelo = vaga livre.</li>
 * </ul>
 * Serve tanto pra escala completa quanto pra de um setor só ({@link EscalaCompletaDTO#setor()}).
 * A4 deitada: um mês normal (8 a 11 dias com missa) cabe numa página.
 */
@Service
public class EscalaPdfService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM", PT_BR);
    private static final DateTimeFormatter ATUALIZADO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PT_BR);

    /** Acima disso as colunas ficam estreitas demais; o mês é quebrado em mais de uma grade. */
    private static final int MAX_DIAS_POR_GRADE = 12;

    /** Ordem das linhas, a mesma da planilha: Holyrics, Datashow, Transmissão, Redes sociais, Jovens, geral. */
    private static final List<Setor> ORDEM_SETORES = java.util.Arrays.asList(
            Setor.HOLYRICS, Setor.DATASHOW, Setor.TRANSMISSOES_VIDEOS, Setor.REDES_SOCIAIS, Setor.JOVENS, null);

    // Mesma paleta do app (index.html): azul da marca, azul profundo e dourado.
    private static final Color AZUL = new Color(0x2B, 0x57, 0xB8);
    private static final Color AZUL_PROFUNDO = new Color(0x14, 0x30, 0x6B);
    private static final Color DOURADO = new Color(0xC7, 0x9A, 0x2E);
    private static final Color DOURADO_CLARO = new Color(0xF8, 0xEE, 0xD2);
    private static final Color TINTA = new Color(0xE8, 0xED, 0xF7);
    private static final Color SEM_FUNCAO = new Color(0xC9, 0xD7, 0xF2);
    private static final Color VAGA = new Color(0xFF, 0xF1, 0xA8);
    private static final Color BORDA = new Color(0xB4, 0xC2, 0xDE);
    private static final Color TEXTO = new Color(0x14, 0x20, 0x3D);
    private static final Color TEXTO_SUAVE = new Color(0x6B, 0x75, 0x90);
    private static final Color AVISO = new Color(0xB4, 0x40, 0x4A);

    private static final Font TITULO = new Font(Font.HELVETICA, 24, Font.BOLD, AZUL_PROFUNDO);
    private static final Font TITULO_MES = new Font(Font.HELVETICA, 20, Font.BOLD, DOURADO);
    private static final Font SUBTITULO = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, TEXTO_SUAVE);
    private static final Font CAB = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);
    private static final Font CAB_DATA = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
    private static final Font CAB_PEQ = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, Color.WHITE);
    private static final Font CAB_EXTRA = new Font(Font.HELVETICA, 7, Font.BOLDITALIC, DOURADO_CLARO);
    private static final Font SETOR = new Font(Font.HELVETICA, 9, Font.BOLD, AZUL_PROFUNDO);
    private static final Font HORARIO = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, TEXTO);
    private static final Font NOME = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXTO);
    private static final Font MONTAGEM = new Font(Font.HELVETICA, 8, Font.ITALIC, new Color(0x6B, 0x75, 0x90));
    private static final Font LIVRE = new Font(Font.HELVETICA, 8.5f, Font.BOLDITALIC, new Color(0x6E, 0x50, 0x0B));
    private static final Font AVISO_FONTE = new Font(Font.HELVETICA, 10, Font.BOLD, AVISO);
    private static final Font LEGENDA = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXTO_SUAVE);

    private static final String MENSAGEM = "Chegue com 1 hora de antecedência para a missa. Vá até a Capela do Santíssimo, "
            + "faça uma oração com calma e prepare seu coração para a celebração. Esse hábito ajuda a cuidar do "
            + "emocional e favorece mais atenção e concentração durante a missa.";

    public byte[] gerar(EscalaCompletaDTO escala) {
        // Título e período já vêm prontos do EventoService.escalaPeriodo ("NOVEMBRO/2026" ou "31/10 A 15/11/2026";
        // "Datashow" ou "Holyrics + Datashow"). Sem nomeSetor = todos os setores.
        String periodo = escala.rotuloPeriodo();
        boolean doSetor = escala.nomeSetor() != null;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 24, 24);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.addTitle((doSetor ? "Escala de " + escala.nomeSetor() : "Escala completa") + " - " + periodo);
            doc.open();

            doc.add(cabecalho(writer, doSetor ? "ESCALA · " + escala.nomeSetor().toUpperCase(PT_BR) : "PASCOM · ESCALA",
                    periodo, doSetor));

            List<EscalaCompletaDTO.EventoEscala> eventos = escala.eventos();
            if (eventos.isEmpty()) {
                Paragraph vazio = new Paragraph(doSetor
                        ? "Nenhuma escala de " + escala.nomeSetor() + " cadastrada nesse período."
                        : "Nenhuma escala cadastrada nesse período.", LIVRE);
                vazio.setAlignment(Element.ALIGN_CENTER);
                vazio.setSpacingBefore(30);
                doc.add(vazio);
            } else {
                List<Linha> linhas = linhas(eventos);
                List<java.time.LocalDate> dias = eventos.stream().map(EscalaCompletaDTO.EventoEscala::data).distinct().sorted().toList();
                for (int i = 0; i < dias.size(); i += MAX_DIAS_POR_GRADE) {
                    PdfPTable grade = grade(linhas, dias.subList(i, Math.min(dias.size(), i + MAX_DIAS_POR_GRADE)), eventos);
                    grade.setSpacingBefore(i == 0 ? 0 : 14);
                    doc.add(grade);
                }
                doc.add(legenda());
            }

            doc.add(mensagem());

            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar o PDF da escala.", e);
        }
        return out.toByteArray();
    }

    /** Título centralizado, com a estrela da marca (a mesma do app) dos dois lados. */
    private PdfPTable cabecalho(PdfWriter writer, String titulo, String mes, boolean doSetor) throws Exception {
        PdfPTable t = new PdfPTable(new float[]{1, 8, 1});
        t.setWidthPercentage(100);

        PdfPCell meio = new PdfPCell();
        meio.setBorder(Rectangle.NO_BORDER);
        meio.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph p1 = new Paragraph(titulo, TITULO);
        p1.setAlignment(Element.ALIGN_CENTER);
        Paragraph p2 = new Paragraph(mes, TITULO_MES);
        p2.setAlignment(Element.ALIGN_CENTER);
        Paragraph p3 = new Paragraph((doSetor ? "Funções dos setores escolhidos e funções gerais" : "Todos os setores da pastoral")
                + " · Atualizado em " + LocalDateTime.now(ZoneId.of(CalendarioService.FUSO)).format(ATUALIZADO), SUBTITULO);
        p3.setAlignment(Element.ALIGN_CENTER);
        p3.setSpacingAfter(8);
        meio.addElement(p1);
        meio.addElement(p2);
        meio.addElement(p3);

        t.addCell(estrela(writer));
        t.addCell(meio);
        t.addCell(estrela(writer));
        t.setSpacingAfter(10);

        // Faixa dourada separando o cabeçalho da grade.
        PdfPCell faixa = new PdfPCell(new Phrase(" "));
        faixa.setColspan(3);
        faixa.setFixedHeight(3);
        faixa.setBorder(Rectangle.NO_BORDER);
        faixa.setBackgroundColor(DOURADO);
        t.addCell(faixa);
        return t;
    }

    private PdfPCell estrela(PdfWriter writer) throws Exception {
        float s = 46;
        PdfTemplate tpl = writer.getDirectContent().createTemplate(s, s);
        float c = s / 2;
        tpl.setColorStroke(DOURADO);
        tpl.setLineWidth(1.2f);
        tpl.circle(c, c, c - 1.5f);
        tpl.stroke();
        // Estrela de 4 pontas do logo do app (path "M20 6L23.4 16.6 34 20 ..." escalado).
        float k = s / 40f;
        float[][] pts = {{20, 6}, {23.4f, 16.6f}, {34, 20}, {23.4f, 23.4f}, {20, 34}, {16.6f, 23.4f}, {6, 20}, {16.6f, 16.6f}};
        tpl.setColorFill(DOURADO);
        tpl.moveTo(pts[0][0] * k, s - pts[0][1] * k);
        for (int i = 1; i < pts.length; i++) tpl.lineTo(pts[i][0] * k, s - pts[i][1] * k);
        tpl.closePath();
        tpl.fill();

        PdfPCell cell = new PdfPCell(Image.getInstance(tpl));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    /**
     * Uma linha da grade: um setor num horário (null = função geral). O Holyrics tem uma linha só,
     * sem horário: a montagem vale pro fim de semana inteiro.
     */
    private record Linha(Setor setor, String nomeSetor, LocalTime horario) {}

    /** Os pares (setor, horário) que aparecem no mês, na ordem da planilha e do horário mais cedo pro mais tarde. */
    private List<Linha> linhas(List<EscalaCompletaDTO.EventoEscala> eventos) {
        List<Linha> resultado = new ArrayList<>();
        for (Setor setor : ORDEM_SETORES) {
            java.util.TreeMap<LocalTime, String> horarios = new java.util.TreeMap<>();
            String nome = null;
            for (EscalaCompletaDTO.EventoEscala ev : eventos) {
                for (EscalaCompletaDTO.GrupoSetor g : ev.setores()) {
                    if (!Objects.equals(g.setor(), setor) || g.funcoes().isEmpty()) continue;
                    nome = g.nomeSetor();
                    horarios.put(setor == Setor.HOLYRICS || ev.horario() == null ? LocalTime.MIDNIGHT : ev.horario(), nome);
                }
            }
            for (LocalTime h : horarios.keySet()) {
                resultado.add(new Linha(setor, nome, setor == Setor.HOLYRICS ? null : h));
            }
        }
        return resultado;
    }

    private PdfPTable grade(List<Linha> linhas, List<java.time.LocalDate> dias, List<EscalaCompletaDTO.EventoEscala> eventos) {
        float[] larguras = new float[2 + dias.size()];
        larguras[0] = 1.55f;
        larguras[1] = 0.95f;
        for (int i = 2; i < larguras.length; i++) larguras[i] = 1.1f;
        PdfPTable t = new PdfPTable(larguras);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        Map<String, String> curtos = nomesCurtos(eventos);

        // Cabeçalho: ATIVIDADE | HORÁRIO | um dia por coluna.
        t.addCell(cabecalhoFixo("ATIVIDADE"));
        t.addCell(cabecalhoFixo("HORÁRIO"));
        for (java.time.LocalDate d : dias) t.addCell(cabecalhoDia(d, doDia(eventos, d)));

        // Corpo: o nome do setor ocupa a altura de todos os horários dele (rowspan).
        for (int i = 0; i < linhas.size(); ) {
            Linha l = linhas.get(i);
            int fim = i;
            while (fim < linhas.size() && Objects.equals(linhas.get(fim).setor(), l.setor())) fim++;

            PdfPCell setor = new PdfPCell(new Phrase(rotuloSetor(l), SETOR));
            setor.setRowspan(fim - i);
            setor.setBackgroundColor(TINTA);
            setor.setBorderColor(BORDA);
            setor.setHorizontalAlignment(Element.ALIGN_CENTER);
            setor.setVerticalAlignment(Element.ALIGN_MIDDLE);
            setor.setPadding(5);
            t.addCell(setor);

            for (int j = i; j < fim; j++) {
                Linha linha = linhas.get(j);
                PdfPCell hora = new PdfPCell(new Phrase(linha.horario() == null ? "" : hora(linha.horario()), HORARIO));
                hora.setBackgroundColor(Color.WHITE);
                hora.setBorderColor(BORDA);
                hora.setHorizontalAlignment(Element.ALIGN_CENTER);
                hora.setVerticalAlignment(Element.ALIGN_MIDDLE);
                hora.setPadding(4);
                t.addCell(hora);
                for (int k = 0; k < dias.size(); k++) {
                    java.time.LocalDate d = dias.get(k);
                    List<SlotEscalaDTO> slots = slots(eventos, linha, d);
                    // Holyrics: a montagem de sábado vale pro domingo também, então ocupa as duas colunas.
                    int colunas = 1;
                    if (linha.setor() == Setor.HOLYRICS && !slots.isEmpty() && k + 1 < dias.size()
                            && dias.get(k + 1).equals(d.plusDays(1)) && slots(eventos, linha, dias.get(k + 1)).isEmpty()) {
                        colunas = 2;
                    }
                    PdfPCell c = celula(slots, curtos);
                    c.setColspan(colunas);
                    t.addCell(c);
                    k += colunas - 1;
                }
            }
            i = fim;
        }
        return t;
    }

    private static List<EscalaCompletaDTO.EventoEscala> doDia(List<EscalaCompletaDTO.EventoEscala> eventos, java.time.LocalDate d) {
        return eventos.stream().filter(e -> e.data().equals(d)).toList();
    }

    /** As funções daquele setor, naquele dia e horário (somando os eventos, se houver mais de um). */
    private static List<SlotEscalaDTO> slots(List<EscalaCompletaDTO.EventoEscala> eventos, Linha linha, java.time.LocalDate d) {
        List<SlotEscalaDTO> r = new ArrayList<>();
        for (EscalaCompletaDTO.EventoEscala ev : eventos) {
            if (!ev.data().equals(d)) continue;
            if (linha.setor() != Setor.HOLYRICS && !Objects.equals(ev.horario() == null ? LocalTime.MIDNIGHT : ev.horario(), linha.horario())) continue;
            for (EscalaCompletaDTO.GrupoSetor g : ev.setores()) {
                if (Objects.equals(g.setor(), linha.setor())) r.addAll(g.funcoes());
            }
        }
        return r;
    }

    private PdfPCell cabecalhoFixo(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, CAB));
        c.setBackgroundColor(AZUL_PROFUNDO);
        c.setBorderColor(AZUL_PROFUNDO);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(6);
        return c;
    }

    /** "05/09" grande e o dia da semana; dia de evento extra mostra o nome dele e, se for grande, fica dourado. */
    private PdfPCell cabecalhoDia(java.time.LocalDate d, List<EscalaCompletaDTO.EventoEscala> doDia) {
        String diaSemana = d.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
        diaSemana = Character.toUpperCase(diaSemana.charAt(0)) + diaSemana.substring(1);

        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_CENTER);
        p.setLeading(11);
        p.add(new Chunk(d.format(DIA) + "\n", CAB_DATA));
        p.add(new Chunk(diaSemana, CAB_PEQ));
        // As missas fixas ("Missa das 10h", "Montagem Holyrics") não precisam aparecer; evento extra mostra o nome.
        List<String> extras = doDia.stream().map(EscalaCompletaDTO.EventoEscala::titulo)
                .filter(ti -> ti != null && !ti.trim().matches("(?i)(missa das \\d{1,2}h(\\d{2})?|montagem holyrics)"))
                .distinct().toList();
        if (!extras.isEmpty()) p.add(new Chunk("\n" + String.join(" · ", extras), CAB_EXTRA));

        PdfPCell c = new PdfPCell();
        c.addElement(p);
        c.setBackgroundColor(doDia.stream().anyMatch(EscalaCompletaDTO.EventoEscala::eventoGrande) ? DOURADO : AZUL);
        c.setBorderColor(AZUL_PROFUNDO);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPaddingTop(3);
        c.setPaddingBottom(6);
        return c;
    }

    /**
     * Nomes de quem serve, um embaixo do outro (sem o nome da função, como na planilha). "Livre" em
     * amarelo se ninguém foi escalado; "+N livres" se faltam algumas; azul se o setor não serve ali.
     */
    private PdfPCell celula(List<SlotEscalaDTO> slots, Map<String, String> curtos) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(BORDA);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
        c.setPaddingBottom(6);
        c.setMinimumHeight(24);

        if (slots.isEmpty()) {
            c.setPhrase(new Phrase(" "));
            c.setBackgroundColor(SEM_FUNCAO);
            return c;
        }
        // Escala em montagem e quem baixou não coordena o setor: não mostra nomes.
        if (slots.stream().allMatch(SlotEscalaDTO::oculto)) {
            c.setPhrase(new Phrase("em montagem", MONTAGEM));
            c.setBackgroundColor(TINTA);
            return c;
        }
        Phrase ph = new Phrase();
        ph.setLeading(11.5f);
        int livres = 0;
        boolean primeiro = true;
        for (SlotEscalaDTO f : slots) {
            if (f.oculto()) continue;
            if (f.voluntarioNome() == null) { livres++; continue; }
            ph.add(new Chunk((primeiro ? "" : "\n") + curtos.getOrDefault(f.voluntarioNome(), f.voluntarioNome()), NOME));
            primeiro = false;
        }
        if (primeiro) {
            ph.add(new Chunk("Livre", LIVRE));
            c.setBackgroundColor(VAGA);
        } else {
            if (livres > 0) ph.add(new Chunk("\n+" + livres + (livres > 1 ? " livres" : " livre"), LIVRE));
            c.setBackgroundColor(Color.WHITE);
        }
        c.setPhrase(ph);
        return c;
    }

    /**
     * Como na planilha, só o primeiro nome ("Vanessa", "Gian"): nome e sobrenome quebravam em duas
     * linhas e pareciam duas pessoas. Se duas pessoas do mês têm o mesmo primeiro nome, ganham a
     * inicial do sobrenome ("Marcelo A.", "Marcelo T.").
     */
    private static Map<String, String> nomesCurtos(List<EscalaCompletaDTO.EventoEscala> eventos) {
        java.util.Set<String> nomes = new java.util.TreeSet<>();
        for (EscalaCompletaDTO.EventoEscala ev : eventos)
            for (EscalaCompletaDTO.GrupoSetor g : ev.setores())
                for (SlotEscalaDTO f : g.funcoes()) if (f.voluntarioNome() != null) nomes.add(f.voluntarioNome().trim());
        Map<String, Integer> porPrimeiro = new java.util.HashMap<>();
        for (String n : nomes) porPrimeiro.merge(n.split("\\s+")[0].toLowerCase(PT_BR), 1, Integer::sum);
        Map<String, String> r = new java.util.HashMap<>();
        for (String n : nomes) {
            String[] partes = n.split("\\s+");
            boolean repetido = porPrimeiro.get(partes[0].toLowerCase(PT_BR)) > 1 && partes.length > 1;
            r.put(n, repetido ? partes[0] + " " + partes[partes.length - 1].charAt(0) + "." : partes[0]);
        }
        return r;
    }

    /** Nome do setor na coluna ATIVIDADE (curto, como na planilha, pra não quebrar no meio da palavra). */
    private static String rotuloSetor(Linha l) {
        if (l.setor() == null) return "GERAL";
        return switch (l.setor()) {
            case TRANSMISSOES_VIDEOS -> "TRANSMISSÃO";
            case REDES_SOCIAIS -> "REDES SOCIAIS";
            default -> l.nomeSetor().toUpperCase(PT_BR);
        };
    }

    private static String hora(LocalTime h) {
        if (h == null) return "";
        return h.getMinute() == 0 ? h.getHour() + "h" : String.format("%dh%02d", h.getHour(), h.getMinute());
    }

    private Paragraph legenda() {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(6);
        p.add(new Chunk("    ", new Font(Font.HELVETICA, 8)).setBackground(VAGA));
        p.add(new Chunk("  vaga livre       ", LEGENDA));
        p.add(new Chunk("    ", new Font(Font.HELVETICA, 8)).setBackground(SEM_FUNCAO));
        p.add(new Chunk("  o setor não serve nesse dia/horário       ", LEGENDA));
        p.add(new Chunk("    ", new Font(Font.HELVETICA, 8)).setBackground(DOURADO));
        p.add(new Chunk("  dia de evento especial       ", LEGENDA));
        p.add(new Chunk("    ", new Font(Font.HELVETICA, 8)).setBackground(TINTA));
        p.add(new Chunk("  em montagem (a coordenação ainda não publicou)", LEGENDA));
        return p;
    }

    /** Recado fixo pra equipe, na caixa do rodapé (o mesmo da escala impressa). */
    private PdfPTable mensagem() {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(12);
        PdfPCell c = new PdfPCell(new Phrase(MENSAGEM, AVISO_FONTE));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setBorderColor(AVISO);
        c.setBorderWidth(1.2f);
        c.setBackgroundColor(new Color(0xFD, 0xF1, 0xF2));
        c.setPadding(9);
        c.setLeading(0, 1.35f);
        t.addCell(c);
        t.setKeepTogether(true);
        return t;
    }
}
