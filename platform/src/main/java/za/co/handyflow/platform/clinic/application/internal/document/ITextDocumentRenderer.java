package za.co.handyflow.platform.clinic.application.internal.document;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.Block;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.Heading;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.KeyValues;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.Letterhead;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.Rule;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.Signature;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.Spacer;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.TextBlock;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static za.co.handyflow.platform.clinic.application.internal.document.DocumentModelRules.clean;
import static za.co.handyflow.platform.clinic.application.internal.document.DocumentModelRules.orDash;

/**
 * The first {@link DocumentRenderer}: iText 7, which the clinic already uses. It exists so documents can move onto the
 * renderer-independent model before the library is replaced; the replacement is chosen by the CLIN-DOC-001A spike
 * (CLINIC-DEC-023). Standard Helvetica only, so non-Latin text is not supported by this adapter.
 */
@Component
public class ITextDocumentRenderer implements DocumentRenderer {

    private static final DeviceRgb TEAL = new DeviceRgb(13, 148, 136);
    private static final DeviceRgb MUTED = new DeviceRgb(120, 130, 140);
    private static final DeviceRgb HEAD_BG = new DeviceRgb(240, 244, 245);

    @Override
    public String name() {
        return "itext-7.2.5";
    }

    @Override
    public byte[] render(DocumentModel m) {
        List<String> problems = DocumentModelRules.problems(m);
        if (!problems.isEmpty()) throw new IllegalArgumentException("The document cannot be made: " + String.join("; ", problems));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfDocument pdf = new PdfDocument(new PdfWriter(out));
            pdf.getDocumentInfo().setTitle(clean(m.title()));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 40, 56, 40);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            if (m.letterhead() != null) letterhead(doc, m.letterhead(), regular, bold);
            for (Block b : m.blocks()) block(doc, b, regular, bold);

            int pages = pdf.getNumberOfPages();
            for (int i = 1; i <= pages; i++) {
                String left = clean(m.footerText());
                if (!left.isEmpty()) doc.showTextAligned(new Paragraph(left).setFont(regular).setFontSize(8).setFontColor(MUTED),
                        40, 28, i, TextAlignment.LEFT, VerticalAlignment.BOTTOM, 0);
                if (m.pageNumbers()) doc.showTextAligned(new Paragraph("Page " + i + " of " + pages).setFont(regular).setFontSize(8).setFontColor(MUTED),
                        PageSize.A4.getWidth() - 40, 28, i, TextAlignment.RIGHT, VerticalAlignment.BOTTOM, 0);
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Document generation failed", e);
        }
    }

    private static void letterhead(Document doc, Letterhead lh, PdfFont regular, PdfFont bold) throws Exception {
        if (lh.logo() != null && lh.logo().length > 0) {
            doc.add(new Image(ImageDataFactory.create(lh.logo())).scaleToFit(140, 56).setMarginBottom(6));
        }
        if (!clean(lh.name()).isEmpty()) doc.add(new Paragraph(clean(lh.name())).setFont(bold).setFontSize(14).setMarginBottom(2));
        for (String line : lh.lines()) {
            if (!clean(line).isEmpty()) doc.add(new Paragraph(clean(line)).setFont(regular).setFontSize(9).setFontColor(MUTED).setMarginBottom(0));
        }
        doc.add(new LineSeparator(new SolidLine(1f)).setMarginTop(8).setMarginBottom(14).setFontColor(TEAL));
    }

    private static void block(Document doc, Block b, PdfFont regular, PdfFont bold) throws Exception {
        if (b instanceof Heading h) {
            float size = h.level() == 1 ? 14 : h.level() == 2 ? 12 : 10.5f;
            Paragraph p = new Paragraph(clean(h.text())).setFont(bold).setFontSize(size).setMarginBottom(8).setKeepWithNext(true);
            if (h.level() == 1) p.setFontColor(TEAL);
            doc.add(p);
        } else if (b instanceof TextBlock p) {
            for (String para : clean(p.text()).split("\\R\\s*\\R")) {
                Paragraph x = new Paragraph(para.trim()).setFont(p.bold() ? bold : regular).setFontSize(10).setMarginBottom(8);
                if (p.muted()) x.setFontColor(MUTED);
                doc.add(x);
            }
        } else if (b instanceof KeyValues kv) {
            Table t = new Table(UnitValue.createPercentArray(new float[]{30, 70})).useAllAvailableWidth().setMarginBottom(10);
            for (String[] pair : kv.pairs()) {
                t.addCell(new Cell().add(new Paragraph(clean(pair[0])).setFont(bold).setFontSize(9)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
                t.addCell(new Cell().add(new Paragraph(orDash(pair[1])).setFont(regular).setFontSize(9)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
            }
            doc.add(t);
        } else if (b instanceof DocumentModel.Grid tb) {
            float[] w = new float[tb.weights().size()];
            for (int i = 0; i < w.length; i++) w[i] = tb.weights().get(i).floatValue();
            Table t = new Table(UnitValue.createPercentArray(w)).useAllAvailableWidth().setMarginBottom(10);
            for (String h : tb.header()) t.addHeaderCell(new Cell().add(new Paragraph(clean(h)).setFont(bold).setFontSize(9)).setBackgroundColor(HEAD_BG));
            for (List<String> row : tb.rows()) for (String c : row) t.addCell(new Cell().add(new Paragraph(orDash(c)).setFont(regular).setFontSize(9)));
            doc.add(t);
        } else if (b instanceof Rule) {
            doc.add(new LineSeparator(new SolidLine(0.5f)).setMarginTop(4).setMarginBottom(8).setFontColor(MUTED));
        } else if (b instanceof Spacer s) {
            doc.add(new Paragraph(" ").setFontSize(Math.max(1, s.points())).setMargin(0));
        } else if (b instanceof Signature s) {
            Div d = new Div().setKeepTogether(true).setMarginTop(14);
            if (!clean(s.closing()).isEmpty()) d.add(new Paragraph(clean(s.closing())).setFont(regular).setFontSize(10).setMarginBottom(6));
            if (s.image() != null && s.image().length > 0) d.add(new Image(ImageDataFactory.create(s.image())).scaleToFit(140, 48).setMarginBottom(4));
            else d.add(new Paragraph(" ").setFontSize(24));
            d.add(new Paragraph(clean(s.signerName())).setFont(bold).setFontSize(11).setMarginBottom(0));
            if (!clean(s.signerLine()).isEmpty()) d.add(new Paragraph(clean(s.signerLine())).setFont(regular).setFontSize(9).setFontColor(MUTED));
            doc.add(d);
        }
    }
}
