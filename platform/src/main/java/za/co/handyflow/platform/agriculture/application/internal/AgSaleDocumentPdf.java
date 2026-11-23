package za.co.handyflow.platform.agriculture.application.internal;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.agriculture.domain.rules.AgSaleDocumentRules;
import za.co.handyflow.platform.agriculture.domain.rules.AgSaleDocumentRules.Line;
import za.co.handyflow.platform.agriculture.domain.rules.AgSaleDocumentRules.Party;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

/**
 * Draws a sale document. It decides nothing: every word, warning and total comes from AgSaleDocumentRules, which is tested.
 * Same library (OpenPDF, com.lowagie.text) and brand colour as the other modules' PDFs.
 */
@Component
public class AgSaleDocumentPdf {

    private static final Color BRAND = new Color(30, 41, 59);
    private static final Color WARN = new Color(180, 83, 9);
    private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD, BRAND);
    private static final Font HEAD = new Font(Font.HELVETICA, 10.5f, Font.BOLD, BRAND);
    private static final Font BODY = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, Color.BLACK);
    private static final Font SMALL = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
    private static final Font WARN_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, WARN);

    public byte[] render(AgSaleDocumentRules.Document doc) {
        Document pdf = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(pdf, out);
            pdf.open();
            pdf.add(new Paragraph(doc.title(), TITLE));
            pdf.add(new Paragraph("Stock Theft Act 57 of 1959, section 6", SMALL));
            pdf.add(new Paragraph("Document no. " + doc.number() + "     Date: " + AgSaleDocumentRules.formatDay(doc.date()), BODY));
            pdf.add(spacer());

            PdfPTable seller = pairs();
            section(seller, "Seller");
            row(seller, "Owner / seller", doc.seller().name());
            row(seller, "Farm", join(doc.farmName(), doc.farmRegistration()));
            row(seller, "Physical address", doc.seller().address());
            row(seller, "Registered brand mark", doc.brandMark());
            row(seller, "Ear mark", doc.earMark());
            row(seller, "Mark registration no.", doc.markRegistrationNumber());
            pdf.add(seller);
            pdf.add(spacer());

            PdfPTable buyer = pairs();
            section(buyer, "Buyer / receiver");
            Party b = doc.buyer();
            row(buyer, "Name", b.name());
            row(buyer, "ID / registration no.", b.idNumber());
            row(buyer, "Address", b.address());
            row(buyer, "Destination", doc.destination());
            row(buyer, "Vehicle registration", doc.vehicleRegistration());
            pdf.add(buyer);
            pdf.add(spacer());

            pdf.add(new Paragraph("Livestock (" + doc.totalHead() + " head)", HEAD));
            PdfPTable lines = new PdfPTable(new float[] {2f, 6f, 1f});
            lines.setWidthPercentage(100);
            header(lines, "Tag / batch"); header(lines, "Description"); header(lines, "Head");
            for (Line l : doc.lines()) { cell(lines, l.tag()); cell(lines, l.description()); cell(lines, String.valueOf(l.head())); }
            pdf.add(lines);

            if (!doc.warnings().isEmpty()) {
                pdf.add(spacer());
                pdf.add(new Paragraph("To complete by hand before signing", WARN_FONT));
                for (String w : doc.warnings()) pdf.add(new Paragraph("- " + w, WARN_FONT));
            }

            pdf.add(spacer());
            PdfPTable sign = new PdfPTable(3);
            sign.setWidthPercentage(100);
            signature(sign, "Seller signature");
            signature(sign, "Buyer signature");
            signature(sign, "Date");
            pdf.add(sign);
            pdf.add(spacer());
            pdf.add(new Paragraph("Prepared from the farm's records in HandyFlow. It supports, and does not replace, what the Stock Theft Act "
                    + "and the police require. Check the current requirements before relying on it.", SMALL));
            pdf.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate the sale document", e);
        }
    }

    private static Paragraph spacer() { return new Paragraph(" ", SMALL); }

    private static PdfPTable pairs() throws DocumentException {
        PdfPTable t = new PdfPTable(new float[] {3f, 7f});
        t.setWidthPercentage(100);
        return t;
    }

    private static void section(PdfPTable t, String title) {
        PdfPCell c = new PdfPCell(new Phrase(title, HEAD));
        c.setColspan(2);
        c.setBorder(PdfPCell.BOTTOM);
        t.addCell(c);
    }

    private static void row(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, SMALL));
        l.setBorder(PdfPCell.NO_BORDER);
        PdfPCell v = new PdfPCell(new Phrase(value == null || value.isBlank() ? "......................................" : value, BODY));
        v.setBorder(PdfPCell.NO_BORDER);
        t.addCell(l);
        t.addCell(v);
    }

    private static void header(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, HEAD));
        c.setBackgroundColor(new Color(241, 245, 249));
        t.addCell(c);
    }

    private static void cell(PdfPTable t, String text) { t.addCell(new PdfPCell(new Phrase(text == null ? "" : text, BODY))); }

    private static void signature(PdfPTable t, String label) {
        PdfPCell c = new PdfPCell(new Phrase("\n\n______________________\n" + label, SMALL));
        c.setBorder(PdfPCell.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.addCell(c);
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) return b;
        return b == null || b.isBlank() ? a : a + " (reg. " + b + ")";
    }
}
