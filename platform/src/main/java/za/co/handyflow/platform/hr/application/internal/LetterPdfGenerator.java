package za.co.handyflow.platform.hr.application.internal;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import org.springframework.stereotype.Component;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.element.Image;
import za.co.handyflow.platform.hr.domain.model.LetterRules;
import za.co.handyflow.platform.hr.domain.model.LetterheadRules.Letterhead;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

/** Makes the PDF of an issued letter from its final wording. Plain text only: a blank line starts a new paragraph, a line break is kept. */
@Component
public class LetterPdfGenerator {

    private static final DeviceRgb NAVY = new DeviceRgb(27, 58, 107);
    private static final DeviceRgb GREY = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb TEXT = new DeviceRgb(15, 23, 42);

    /** A letter with only the company's name at the top. */
    public byte[] generate(String companyName, String number, LocalDate issuedOn, String subject, String body) {
        return generate(Letterhead.plain(companyName), number, issuedOn, subject, body);
    }

    public byte[] generate(Letterhead head, String number, LocalDate issuedOn, String subject, String body) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfDocument pdf = new PdfDocument(new PdfWriter(out));
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new Footer(head.name() + " · " + number));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(60, 60, 70, 60);

            if (head.hasLogo()) {
                try {
                    Image logo = new Image(ImageDataFactory.create(head.logo()));
                    logo.scaleToFit(140, 56).setMarginBottom(6);
                    doc.add(logo);
                } catch (Exception ignored) {
                    // a logo that cannot be read is left out; the letter still goes ahead
                }
            }
            doc.add(new Paragraph(head.name()).setFontSize(18).setBold().setFontColor(NAVY).setMarginBottom(2));
            for (String line : head.lines()) doc.add(new Paragraph(line).setFontSize(9).setFontColor(GREY).setMarginBottom(0));
            doc.add(new Paragraph("Ref " + number + " · " + LetterRules.longDate(issuedOn)).setFontSize(9).setFontColor(GREY).setMarginTop(6).setMarginBottom(6));
            doc.add(new Paragraph().setBorderBottom(new SolidBorder(NAVY, 1.5f)).setMarginTop(4).setMarginBottom(12));
            doc.add(new Paragraph(subject).setFontSize(13).setBold().setFontColor(TEXT).setMarginBottom(12));

            for (String block : body.replace("\r\n", "\n").split("\n\\s*\n")) {
                String text = block.strip();
                if (text.isEmpty()) continue;
                doc.add(new Paragraph(text).setFontSize(10.5f).setFontColor(TEXT).setTextAlignment(TextAlignment.LEFT)
                        .setMultipliedLeading(1.25f).setMarginBottom(10));
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("The letter could not be made into a PDF: " + e.getMessage(), e);
        }
    }

    private static final class Footer implements IEventHandler {
        private final String text;
        Footer(String text) { this.text = text; }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent e = (PdfDocumentEvent) event;
            PdfDocument doc = e.getDocument();
            PdfPage page = e.getPage();
            int n = doc.getPageNumber(page);
            new PdfCanvas(page).beginText().setFontAndSize(doc.getDefaultFont(), 8).moveText(60, 30)
                    .showText(text + " · page " + n).endText();
        }
    }
}
