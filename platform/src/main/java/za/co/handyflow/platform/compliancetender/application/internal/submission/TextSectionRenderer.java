package za.co.handyflow.platform.compliancetender.application.internal.submission;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Draws a text section as plain, readable A4 pages: the section title, the tender's name, then the text
 * line by line. A line starting with "## " is a sub-heading. Deliberately plain: layout and branding are a
 * later concern, and a plain section is the same in every viewer.
 * <p>
 * UNVERIFIED in the authoring session (iText could not be downloaded there); covered by
 * {@code TextSectionRendererTest}, whose first Maven run is the check.
 */
@Component
public class TextSectionRenderer implements SectionRenderer {

    @Override
    public byte[] render(String tenderTitle, SectionType section, String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out)); Document doc = new Document(pdf, PageSize.A4)) {
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA, "Cp1252", EmbeddingStrategy.PREFER_NOT_EMBEDDED);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD, "Cp1252", EmbeddingStrategy.PREFER_NOT_EMBEDDED);
            doc.setMargins(54, 54, 54, 54);
            doc.add(new Paragraph(section.title()).setFont(bold).setFontSize(18));
            if (tenderTitle != null && !tenderTitle.isBlank()) doc.add(new Paragraph(tenderTitle).setFont(regular).setFontSize(10).setMarginBottom(14));
            for (String line : (text == null ? "" : text).split("\\R", -1)) {
                if (line.startsWith("## ")) {
                    doc.add(new Paragraph(line.substring(3)).setFont(bold).setFontSize(12).setMarginTop(10));
                } else {
                    doc.add(new Paragraph(line.isEmpty() ? " " : line).setFont(regular).setFontSize(10).setMarginBottom(2));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The section could not be drawn.", e);
        }
        return out.toByteArray();
    }
}
