package br.org.pascom.service;

import br.org.pascom.dto.EscalaCompletaDTO;
import br.org.pascom.dto.SlotEscalaDTO;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Gera o PDF da escala completa do mês (todos os setores juntos), pra qualquer pessoa baixar/imprimir. */
@Service
public class EscalaPdfService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM", PT_BR);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", PT_BR);
    private static final DateTimeFormatter GERADO_EM = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PT_BR);

    private static final Color AZUL = new Color(0x1F, 0x3A, 0x5F);
    private static final Color CINZA_CLARO = new Color(0xF1, 0xF3, 0xF6);
    private static final Color CINZA_BORDA = new Color(0xD5, 0xDA, 0xE1);
    private static final Color CINZA_TEXTO = new Color(0x6B, 0x72, 0x80);

    private static final Font TITULO = new Font(Font.HELVETICA, 18, Font.BOLD, AZUL);
    private static final Font SUBTITULO = new Font(Font.HELVETICA, 10, Font.NORMAL, CINZA_TEXTO);
    private static final Font EVENTO = new Font(Font.HELVETICA, 12, Font.BOLD, AZUL);
    private static final Font EVENTO_INFO = new Font(Font.HELVETICA, 9, Font.NORMAL, CINZA_TEXTO);
    private static final Font CABECALHO = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font CELULA = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font CELULA_SETOR = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
    private static final Font VAGA = new Font(Font.HELVETICA, 10, Font.ITALIC, CINZA_TEXTO);

    public byte[] gerar(EscalaCompletaDTO escala) {
        YearMonth mes = YearMonth.parse(escala.mes());
        String nomeMes = mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        nomeMes = Character.toUpperCase(nomeMes.charAt(0)) + nomeMes.substring(1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 40, 40);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Escala completa - " + nomeMes + " de " + mes.getYear());
            doc.open();

            doc.add(new Paragraph("Escala completa — " + nomeMes + " de " + mes.getYear(), TITULO));
            Paragraph sub = new Paragraph("Pascom Santuário N. Sra. de Fátima · todos os setores · "
                    + escala.domingos().size() + " domingos no mês", SUBTITULO);
            sub.setSpacingAfter(14);
            doc.add(sub);

            if (escala.eventos().isEmpty()) {
                doc.add(new Paragraph("Nenhuma escala cadastrada para este mês ainda.", VAGA));
            }

            for (EscalaCompletaDTO.EventoEscala ev : escala.eventos()) {
                String diaSemana = ev.data().getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
                diaSemana = Character.toUpperCase(diaSemana.charAt(0)) + diaSemana.substring(1);

                Paragraph cab = new Paragraph(diaSemana + ", " + ev.data().format(DIA) + " — " + ev.titulo(), EVENTO);
                cab.setSpacingBefore(10);
                doc.add(cab);
                Paragraph info = new Paragraph(ev.horario().format(HORA) + " · " + ev.local()
                        + (ev.vagasAbertas() > 0 ? " · " + ev.vagasAbertas() + (ev.vagasAbertas() > 1 ? " vagas abertas" : " vaga aberta") : " · escala completa"),
                        EVENTO_INFO);
                info.setSpacingAfter(4);
                doc.add(info);

                PdfPTable tabela = new PdfPTable(new float[]{2.2f, 2.6f, 3.6f});
                tabela.setWidthPercentage(100);
                tabela.setHeaderRows(1);
                tabela.addCell(cabecalho("Setor"));
                tabela.addCell(cabecalho("Função"));
                tabela.addCell(cabecalho("Quem serve"));

                boolean zebra = false;
                for (EscalaCompletaDTO.GrupoSetor grupo : ev.setores()) {
                    boolean primeira = true;
                    for (SlotEscalaDTO f : grupo.funcoes()) {
                        Color fundo = zebra ? CINZA_CLARO : Color.WHITE;
                        tabela.addCell(celula(primeira ? grupo.nomeSetor() : "", CELULA_SETOR, fundo));
                        tabela.addCell(celula(f.rotulo(), CELULA, fundo));
                        tabela.addCell(f.voluntarioNome() != null
                                ? celula(f.voluntarioNome(), CELULA, fundo)
                                : celula("vaga aberta", VAGA, fundo));
                        primeira = false;
                    }
                    zebra = !zebra;
                }
                doc.add(tabela);
            }

            Paragraph rodape = new Paragraph("Gerado em " + LocalDateTime.now(ZoneId.of(CalendarioService.FUSO)).format(GERADO_EM), SUBTITULO);
            rodape.setSpacingBefore(18);
            rodape.setAlignment(Element.ALIGN_RIGHT);
            doc.add(rodape);

            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar o PDF da escala.", e);
        }
        return out.toByteArray();
    }

    private PdfPCell cabecalho(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, CABECALHO));
        c.setBackgroundColor(AZUL);
        c.setBorderColor(AZUL);
        c.setPadding(6);
        return c;
    }

    private PdfPCell celula(String texto, Font fonte, Color fundo) {
        PdfPCell c = new PdfPCell(new Phrase(texto, fonte));
        c.setBackgroundColor(fundo);
        c.setBorderColor(CINZA_BORDA);
        c.setPadding(5);
        return c;
    }
}
