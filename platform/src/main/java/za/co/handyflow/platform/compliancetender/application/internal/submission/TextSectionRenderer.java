package za.co.handyflow.platform.compliancetender.application.internal.submission;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draws a text section as designed A4 pages: a letterhead (logo, company, contact details) on the first page,
 * a thin running header on later pages, and a footer naming the company, the tender and the section.
 * <ul>
 *   <li><b>Cover letter</b>: laid out as a letter — date, addressee, a "Re:" line, then the text as written.</li>
 *   <li><b>Company profile</b>: lines written as "Label: value" become a facts table; "## " lines are headings.</li>
 *   <li><b>Everything else</b>: a titled page with the text line by line.</li>
 * </ul>
 * The person's text is never changed or extended. A missing or broken logo, or a missing font file, degrades to
 * the plain version rather than failing the build.
 * <p>
 * Covered by {@code TextSectionRendererTest}; the first Maven run is the check.
 */
@Slf4j
@Component
public class TextSectionRenderer implements SectionRenderer {

    private static final DeviceRgb NAVY = new DeviceRgb(0x1B, 0x3A, 0x6B);
    private static final DeviceRgb TEAL = new DeviceRgb(0x0D, 0x94, 0x88);
    private static final DeviceRgb PALE = new DeviceRgb(0xF1, 0xF5, 0xF9);
    private static final DeviceRgb RULE = new DeviceRgb(0xE2, 0xE8, 0xF0);
    private static final DeviceRgb MUTED = new DeviceRgb(0x64, 0x74, 0x8B);
    private static final DeviceRgb INK = new DeviceRgb(0x0F, 0x17, 0x2A);

    private static final DateTimeFormatter LETTER_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final Pattern FACT = Pattern.compile("^([^:#]{1,40}):\\s+(\\S.*)$");

    @Override
    public byte[] render(Letterhead letterhead, SectionType section, String text) {
        Letterhead lh = letterhead == null ? Letterhead.plain(null) : letterhead;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out))) {
            Fonts fonts = fonts();
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new PageFurniture(lh, section.title(), fonts));
            try (Document doc = new Document(pdf, PageSize.A4)) {
                doc.setMargins(46, 54, 62, 54);
                doc.setFont(fonts.regular);
                addLetterhead(doc, lh, fonts);
                String body = text == null ? "" : text;
                if ("COVER_LETTER".equals(section.key())) {
                    addLetterOpening(doc, lh, fonts);
                    addLines(doc, body, fonts, false);
                } else {
                    addTitle(doc, section.title(), lh, fonts);
                    addLines(doc, body, fonts, "COMPANY_PROFILE".equals(section.key()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The section could not be drawn.", e);
        }
        return out.toByteArray();
    }

    // ── Letterhead ────────────────────────────────────────────────────────────

    private void addLetterhead(Document doc, Letterhead lh, Fonts f) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{1.1f, 1f})).setWidth(UnitValue.createPercentValue(100));
        Cell left = new Cell().setBorder(Border.NO_BORDER).setPadding(0);
        Image logo = logoOf(lh.logo());
        if (logo != null) {
            left.add(logo.setMaxHeight(46).setMaxWidth(150).setMarginBottom(4));
        }
        if (lh.hasCompany()) {
            left.add(new Paragraph(lh.companyName()).setFont(f.bold).setFontSize(logo == null ? 17 : 12).setFontColor(NAVY).setMargin(0));
        }
        Cell right = new Cell().setBorder(Border.NO_BORDER).setPadding(0).setTextAlignment(TextAlignment.RIGHT);
        for (String line : lh.contactLines()) {
            right.add(new Paragraph(line).setFontSize(8.5f).setFontColor(MUTED).setMargin(0).setMultipliedLeading(1.25f));
        }
        table.addCell(left);
        table.addCell(right);
        doc.add(table);
        Table rule = new Table(UnitValue.createPercentArray(new float[]{1})).setWidth(UnitValue.createPercentValue(100)).setMarginTop(8).setMarginBottom(18);
        rule.addCell(new Cell().setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(TEAL, 2.5f)).setPadding(0).setHeight(1));
        doc.add(rule);
    }

    private static Image logoOf(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            return new Image(ImageDataFactory.create(bytes));
        } catch (Exception e) {
            log.warn("The logo could not be read; the letterhead will show the name only: {}", e.getMessage());
            return null;
        }
    }

    // ── Cover letter ──────────────────────────────────────────────────────────

    private void addLetterOpening(Document doc, Letterhead lh, Fonts f) {
        doc.add(new Paragraph(LocalDate.now(ZoneId.of("Africa/Johannesburg")).format(LETTER_DATE)).setFontSize(10).setFontColor(MUTED).setMarginBottom(12));
        if (notBlank(lh.authority())) {
            doc.add(new Paragraph("The Bid Committee").setFont(f.bold).setFontSize(10.5f).setMargin(0));
            doc.add(new Paragraph(lh.authority()).setFontSize(10.5f).setMargin(0).setMarginBottom(12));
        }
        List<String> re = new ArrayList<>();
        if (notBlank(lh.authorityReference())) re.add("Tender " + lh.authorityReference().trim());
        else if (notBlank(lh.tenderNumber())) re.add("Tender " + lh.tenderNumber().trim());
        if (notBlank(lh.tenderName())) re.add(lh.tenderName().trim());
        if (!re.isEmpty()) {
            doc.add(new Paragraph("Re: " + String.join(" — ", re)).setFont(f.bold).setFontSize(11).setFontColor(NAVY).setMarginBottom(2));
        }
        if (notBlank(lh.closingDate())) {
            doc.add(new Paragraph("Closing date: " + lh.closingDate()).setFontSize(9).setFontColor(MUTED).setMarginBottom(14));
        } else {
            doc.add(new Paragraph(" ").setFontSize(6).setMarginBottom(6));
        }
    }

    // ── Titled sections ───────────────────────────────────────────────────────

    private void addTitle(Document doc, String title, Letterhead lh, Fonts f) {
        doc.add(new Paragraph(title).setFont(f.bold).setFontSize(20).setFontColor(NAVY).setMargin(0));
        List<String> sub = new ArrayList<>();
        if (notBlank(lh.tenderNumber())) sub.add(lh.tenderNumber().trim());
        if (notBlank(lh.tenderName())) sub.add(lh.tenderName().trim());
        if (!sub.isEmpty()) {
            doc.add(new Paragraph(String.join(" · ", sub)).setFontSize(9.5f).setFontColor(MUTED).setMarginTop(2).setMarginBottom(14));
        } else {
            doc.add(new Paragraph(" ").setFontSize(6).setMarginBottom(8));
        }
    }

    // ── Body ──────────────────────────────────────────────────────────────────

    private void addLines(Document doc, String body, Fonts f, boolean facts) {
        List<String[]> pending = new ArrayList<>();
        for (String line : body.split("\\R", -1)) {
            Matcher m = FACT.matcher(line.trim());
            if (facts && !line.startsWith("## ") && m.matches()) {
                pending.add(new String[]{m.group(1).trim(), m.group(2).trim()});
                continue;
            }
            flushFacts(doc, pending, f);
            if (line.startsWith("## ")) {
                doc.add(new Paragraph(line.substring(3).trim()).setFont(f.bold).setFontSize(12).setFontColor(TEAL).setMarginTop(14).setMarginBottom(4).setKeepWithNext(true));
            } else {
                doc.add(new Paragraph(line.isEmpty() ? " " : line).setFontSize(10.5f).setFontColor(INK).setMarginBottom(3).setMultipliedLeading(1.3f));
            }
        }
        flushFacts(doc, pending, f);
    }

    private void flushFacts(Document doc, List<String[]> rows, Fonts f) {
        if (rows.isEmpty()) return;
        Table table = new Table(UnitValue.createPercentArray(new float[]{1f, 2.2f})).setWidth(UnitValue.createPercentValue(100)).setMarginBottom(6);
        boolean shade = false;
        for (String[] row : rows) {
            Cell k = new Cell().setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(RULE, 0.75f)).setPaddings(6, 8, 6, 8);
            Cell v = new Cell().setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(RULE, 0.75f)).setPaddings(6, 8, 6, 8);
            if (shade) { k.setBackgroundColor(PALE); v.setBackgroundColor(PALE); }
            k.add(new Paragraph(row[0]).setFont(f.bold).setFontSize(9.5f).setFontColor(MUTED).setMargin(0));
            v.add(new Paragraph(row[1]).setFontSize(10.5f).setFontColor(INK).setMargin(0));
            table.addCell(k);
            table.addCell(v);
            shade = !shade;
        }
        doc.add(table);
        rows.clear();
    }

    // ── Page furniture ────────────────────────────────────────────────────────

    /** A thin running header from page 2, and the footer on every page. Drawn after the page's content. */
    private static final class PageFurniture implements IEventHandler {
        private final Letterhead lh;
        private final String sectionTitle;
        private final Fonts fonts;

        PageFurniture(Letterhead lh, String sectionTitle, Fonts fonts) { this.lh = lh; this.sectionTitle = sectionTitle; this.fonts = fonts; }

        @Override
        public void handleEvent(Event event) {
            try {
                PdfDocumentEvent e = (PdfDocumentEvent) event;
                PdfDocument pdf = e.getDocument();
                PdfPage page = e.getPage();
                int number = pdf.getPageNumber(page);
                PdfCanvas canvas = new PdfCanvas(page);
                if (number > 1 && lh.hasCompany()) {
                    canvas.beginText().setFontAndSize(fonts.bold, 8.5f).setColor(NAVY, true).moveText(54, 800).showText(lh.companyName()).endText();
                    canvas.setStrokeColor(TEAL).setLineWidth(1.2f).moveTo(54, 794).lineTo(541, 794).stroke();
                }
                canvas.setStrokeColor(RULE).setLineWidth(0.75f).moveTo(54, 44).lineTo(541, 44).stroke();
                List<String> parts = new ArrayList<>();
                if (lh.hasCompany()) parts.add(lh.companyName());
                if (notBlank(lh.tenderNumber())) parts.add(lh.tenderNumber());
                parts.add(sectionTitle);
                canvas.beginText().setFontAndSize(fonts.regular, 8).setColor(MUTED, true).moveText(54, 30).showText(String.join("  ·  ", parts)).endText();
                String pageText = "Page " + number;
                float width = fonts.regular.getWidth(pageText, 8);
                canvas.beginText().setFontAndSize(fonts.regular, 8).setColor(MUTED, true).moveText(541 - width, 30).showText(pageText).endText();
                canvas.release();
            } catch (Exception ex) {
                log.warn("The page footer could not be drawn: {}", ex.getMessage());
            }
        }
    }

    // ── Fonts ─────────────────────────────────────────────────────────────────

    private record Fonts(PdfFont regular, PdfFont bold) {}

    /** The platform's embedded Liberation Sans; plain Helvetica if the files cannot be loaded. */
    private static Fonts fonts() throws IOException {
        try (InputStream r = TextSectionRenderer.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf");
             InputStream b = TextSectionRenderer.class.getResourceAsStream("/fonts/LiberationSans-Bold.ttf")) {
            if (r != null && b != null) {
                return new Fonts(
                        PdfFontFactory.createFont(r.readAllBytes(), PdfEncodings.WINANSI, EmbeddingStrategy.FORCE_EMBEDDED),
                        PdfFontFactory.createFont(b.readAllBytes(), PdfEncodings.WINANSI, EmbeddingStrategy.FORCE_EMBEDDED));
            }
        } catch (Exception e) {
            log.warn("The embedded fonts could not be loaded; using Helvetica: {}", e.getMessage());
        }
        return new Fonts(
                PdfFontFactory.createFont(StandardFonts.HELVETICA, "Cp1252", EmbeddingStrategy.PREFER_NOT_EMBEDDED),
                PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD, "Cp1252", EmbeddingStrategy.PREFER_NOT_EMBEDDED));
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
