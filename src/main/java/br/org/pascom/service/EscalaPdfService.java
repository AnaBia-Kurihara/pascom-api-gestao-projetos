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
import java.time.YearMonth;
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
 * Gera o PDF da escala do mês em formato de grade (igual à escala impressa que a Pascom já usa):
 * cada linha é uma função de um setor, cada coluna é uma celebração do mês, e na célula vai quem
 * serve. Serve tanto pra escala completa (todos os setores) quanto pra de um setor só, conforme
 * {@link EscalaCompletaDTO#setor()}. Página deitada (A4 paisagem) pra caber as datas do mês.
 */
@Service
public class EscalaPdfService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM", PT_BR);
    private static final DateTimeFormatter GERADO_EM = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PT_BR);

    /** Acima disso as colunas ficam estreitas demais; o mês é quebrado em mais de uma grade. */
    private static final int MAX_COLUNAS_POR_GRADE = 9;

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
    private static final Font FUNCAO = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TEXTO);
    private static final Font NOME = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXTO);
    private static final Font MONTAGEM = new Font(Font.HELVETICA, 8, Font.ITALIC, new Color(0x6B, 0x75, 0x90));
    private static final Font LIVRE = new Font(Font.HELVETICA, 8.5f, Font.BOLDITALIC, new Color(0x6E, 0x50, 0x0B));
    private static final Font RODAPE_VAGAS = new Font(Font.HELVETICA, 8, Font.BOLD, TEXTO_SUAVE);
    private static final Font RODAPE_OK = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(0x2C, 0x7A, 0x57));
    private static final Font AVISO_FONTE = new Font(Font.HELVETICA, 10, Font.BOLD, AVISO);
    private static final Font LEGENDA = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXTO_SUAVE);

    private static final String MENSAGEM = "Chegue com 1 hora de antecedência para a missa. Vá até a Capela do Santíssimo, "
            + "faça uma oração com calma e prepare seu coração para a celebração. Esse hábito ajuda a cuidar do "
            + "emocional e favorece mais atenção e concentração durante a missa.";

    /** Uma linha da grade: uma função (rótulo) dentro de um setor (null = função geral). */
    private record Linha(Setor setor, String nomeSetor, String rotulo) {}

    public byte[] gerar(EscalaCompletaDTO escala) {
        YearMonth mes = YearMonth.parse(escala.mes());
        String nomeMes = mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR).toUpperCase(PT_BR);
        boolean doSetor = escala.setor() != null;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 24, 24);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.addTitle((doSetor ? "Escala de " + escala.nomeSetor() : "Escala completa") + " - " + nomeMes + "/" + mes.getYear());
            doc.open();

            doc.add(cabecalho(writer, doSetor ? "ESCALA · " + escala.nomeSetor().toUpperCase(PT_BR) : "PASCOM · ESCALA",
                    nomeMes + "/" + mes.getYear(), doSetor));

            List<EscalaCompletaDTO.EventoEscala> eventos = escala.eventos();
            if (eventos.isEmpty()) {
                Paragraph vazio = new Paragraph(doSetor
                        ? "Nenhuma escala de " + escala.nomeSetor() + " cadastrada para este mês ainda."
                        : "Nenhuma escala cadastrada para este mês ainda.", LIVRE);
                vazio.setAlignment(Element.ALIGN_CENTER);
                vazio.setSpacingBefore(30);
                doc.add(vazio);
            } else {
                List<Linha> linhas = linhas(eventos);
                for (int i = 0; i < eventos.size(); i += MAX_COLUNAS_POR_GRADE) {
                    PdfPTable grade = grade(linhas, eventos.subList(i, Math.min(eventos.size(), i + MAX_COLUNAS_POR_GRADE)));
                    grade.setSpacingBefore(i == 0 ? 0 : 14);
                    doc.add(grade);
                }
                doc.add(legenda());
            }

            doc.add(mensagem());

            Paragraph gerado = new Paragraph("Gerado em " + LocalDateTime.now(ZoneId.of(CalendarioService.FUSO)).format(GERADO_EM)
                    + " · Pascom Santuário N. Sra. de Fátima", LEGENDA);
            gerado.setAlignment(Element.ALIGN_RIGHT);
            gerado.setSpacingBefore(6);
            doc.add(gerado);

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
        Paragraph p3 = new Paragraph(doSetor ? "Funções do setor e funções gerais" : "Todos os setores da pastoral", SUBTITULO);
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
     * Junta as funções de todos os eventos do mês, sem repetir (o rótulo é comparado sem
     * diferenciar maiúsculas), com os setores na ordem fixa do DTO. Dentro de cada setor, a ordem
     * é a posição média da função nas missas: assim "Câmera 1, 2, 3, Corte..." continua na ordem
     * certa mesmo quando a coordenação renomeia ou remove uma função de uma missa só.
     */
    private List<Linha> linhas(List<EscalaCompletaDTO.EventoEscala> eventos) {
        List<Setor> ordemSetores = new ArrayList<>(List.of(Setor.values()));
        ordemSetores.add(null);

        List<Linha> resultado = new ArrayList<>();
        for (Setor setor : ordemSetores) {
            Map<String, Linha> porChave = new LinkedHashMap<>();
            Map<String, double[]> posicao = new LinkedHashMap<>(); // {soma das posições, quantidade}
            for (EscalaCompletaDTO.EventoEscala ev : eventos) {
                for (EscalaCompletaDTO.GrupoSetor g : ev.setores()) {
                    if (!Objects.equals(g.setor(), setor)) continue;
                    for (int i = 0; i < g.funcoes().size(); i++) {
                        SlotEscalaDTO f = g.funcoes().get(i);
                        String rotulo = f.rotulo() == null ? "" : f.rotulo().trim();
                        String chave = chave(setor, rotulo);
                        porChave.putIfAbsent(chave, new Linha(setor, g.nomeSetor(), rotulo));
                        double[] p = posicao.computeIfAbsent(chave, k -> new double[2]);
                        p[0] += i;
                        p[1]++;
                    }
                }
            }
            List<String> chaves = new ArrayList<>(porChave.keySet());
            // Sort estável: empate na posição média mantém a ordem em que a função apareceu primeiro.
            chaves.sort(java.util.Comparator.comparingDouble(k -> posicao.get(k)[0] / posicao.get(k)[1]));
            for (String k : chaves) resultado.add(porChave.get(k));
        }
        return resultado;
    }

    private static String chave(Setor setor, String rotulo) {
        return (setor == null ? "-" : setor.name()) + "|" + rotulo.toLowerCase(PT_BR);
    }

    private PdfPTable grade(List<Linha> linhas, List<EscalaCompletaDTO.EventoEscala> eventos) {
        float[] larguras = new float[2 + eventos.size()];
        larguras[0] = 1.45f;
        larguras[1] = 1.45f;
        for (int i = 2; i < larguras.length; i++) larguras[i] = 1.15f;
        PdfPTable t = new PdfPTable(larguras);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);

        // Cabeçalho: SETOR | FUNÇÃO | uma coluna por celebração.
        t.addCell(cabecalhoFixo("SETOR"));
        t.addCell(cabecalhoFixo("FUNÇÃO"));
        for (EscalaCompletaDTO.EventoEscala ev : eventos) t.addCell(cabecalhoEvento(ev));

        // Corpo: o nome do setor ocupa a altura de todas as funções dele (rowspan).
        for (int i = 0; i < linhas.size(); ) {
            Linha l = linhas.get(i);
            int fim = i;
            while (fim < linhas.size() && Objects.equals(linhas.get(fim).setor(), l.setor())) fim++;

            PdfPCell setor = new PdfPCell(new Phrase(l.nomeSetor().toUpperCase(PT_BR), SETOR));
            setor.setRowspan(fim - i);
            setor.setBackgroundColor(TINTA);
            setor.setBorderColor(BORDA);
            setor.setVerticalAlignment(Element.ALIGN_MIDDLE);
            setor.setPaddingLeft(7);
            t.addCell(setor);

            for (int j = i; j < fim; j++) {
                Linha linha = linhas.get(j);
                PdfPCell funcao = new PdfPCell(new Phrase(linha.rotulo(), FUNCAO));
                funcao.setBackgroundColor(Color.WHITE);
                funcao.setBorderColor(BORDA);
                funcao.setVerticalAlignment(Element.ALIGN_MIDDLE);
                funcao.setPadding(5);
                t.addCell(funcao);
                for (EscalaCompletaDTO.EventoEscala ev : eventos) t.addCell(celula(linha, ev));
            }
            i = fim;
        }

        // Rodapé da grade: quantas vagas ainda faltam em cada celebração.
        PdfPCell rot = new PdfPCell(new Phrase("VAGAS ABERTAS", RODAPE_VAGAS));
        rot.setColspan(2);
        rot.setBackgroundColor(TINTA);
        rot.setBorderColor(BORDA);
        rot.setPadding(5);
        rot.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(rot);
        for (EscalaCompletaDTO.EventoEscala ev : eventos) {
            boolean emMontagem = ev.setores().stream().flatMap(g -> g.funcoes().stream()).anyMatch(SlotEscalaDTO::oculto);
            boolean completa = ev.vagasAbertas() == 0;
            String texto = emMontagem && completa ? "em montagem" : completa ? "completa" : String.valueOf(ev.vagasAbertas());
            PdfPCell c = new PdfPCell(new Phrase(texto, emMontagem && completa ? RODAPE_VAGAS : completa ? RODAPE_OK : RODAPE_VAGAS));
            c.setBackgroundColor(TINTA);
            c.setBorderColor(BORDA);
            c.setPadding(5);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            t.addCell(c);
        }
        return t;
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

    /** Data grande, dia da semana e horário; evento extra mostra o título e ganha fundo dourado. */
    private PdfPCell cabecalhoEvento(EscalaCompletaDTO.EventoEscala ev) {
        String diaSemana = ev.data().getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
        diaSemana = Character.toUpperCase(diaSemana.charAt(0)) + diaSemana.substring(1);

        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_CENTER);
        p.setLeading(11);
        p.add(new Chunk(ev.data().format(DIA) + "\n", CAB_DATA));
        p.add(new Chunk(diaSemana.replace("-feira", "") + " · " + hora(ev.horario()), CAB_PEQ));
        // As missas fixas ("Missa das 10h", "Missa das 16h") não precisam repetir o título; evento extra mostra.
        boolean extra = ev.titulo() == null || !ev.titulo().trim().matches("(?i)missa das \\d{1,2}h(\\d{2})?");
        if (extra) p.add(new Chunk("\n" + ev.titulo(), CAB_EXTRA));

        PdfPCell c = new PdfPCell();
        c.addElement(p);
        c.setBackgroundColor(ev.eventoGrande() ? DOURADO : AZUL);
        c.setBorderColor(AZUL_PROFUNDO);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPaddingTop(3);
        c.setPaddingBottom(6);
        return c;
    }

    /** Nome(s) de quem serve; "Livre" em amarelo se a vaga está aberta; azul se a função não existe nesse dia. */
    private PdfPCell celula(Linha linha, EscalaCompletaDTO.EventoEscala ev) {
        List<SlotEscalaDTO> slots = new ArrayList<>();
        for (EscalaCompletaDTO.GrupoSetor g : ev.setores()) {
            if (!Objects.equals(g.setor(), linha.setor())) continue;
            for (SlotEscalaDTO f : g.funcoes()) {
                String rotulo = f.rotulo() == null ? "" : f.rotulo().trim();
                if (rotulo.equalsIgnoreCase(linha.rotulo())) slots.add(f);
            }
        }

        PdfPCell c = new PdfPCell();
        c.setBorderColor(BORDA);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
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
        int livres = 0;
        boolean primeiro = true;
        for (SlotEscalaDTO f : slots) {
            if (f.voluntarioNome() == null) { livres++; continue; }
            ph.add(new Chunk((primeiro ? "" : "\n") + doisPrimeirosNomes(f.voluntarioNome()), NOME));
            primeiro = false;
        }
        if (livres > 0) {
            ph.add(new Chunk((primeiro ? "" : "\n") + (livres > 1 ? livres + " vagas livres" : "Livre"), LIVRE));
        }
        c.setPhrase(ph);
        c.setBackgroundColor(primeiro ? VAGA : Color.WHITE);
        return c;
    }

    /** Na grade cabe melhor só os dois primeiros nomes ("João Pedro", "Fabiana Souza"), como na escala impressa. */
    private static String doisPrimeirosNomes(String nome) {
        String[] partes = nome.trim().split("\\s+");
        if (partes.length <= 2) return nome.trim();
        return partes[0] + " " + partes[1];
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
        p.add(new Chunk("  função não acontece nesse dia       ", LEGENDA));
        p.add(new Chunk("    ", new Font(Font.HELVETICA, 8)).setBackground(DOURADO));
        p.add(new Chunk("  evento grande/especial       ", LEGENDA));
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
