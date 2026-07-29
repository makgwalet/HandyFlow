package za.co.handyflow.platform.invoicing.application.internal;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Generates the statement of account PDF — its own document, same
 * rationale as ReceiptPdfService/CreditNotePdfService: self-contained,
 * no coupling to the tax-invoice generator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatementOfAccountPdfService {

    private final StatementOfAccountService statementService;
    private final TenantFacade tenantFacade;

    private static final DeviceRgb BRAND_DARK = new DeviceRgb(15, 76, 92);
    private static final DeviceRgb TEAL       = new DeviceRgb(13, 148, 136);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(120, 130, 140);
    private static final DeviceRgb BG_LIGHT   = new DeviceRgb(240, 250, 246);
    private static final DeviceRgb RED        = new DeviceRgb(180, 60, 50);
    private static final DeviceRgb WHITE      = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb BORDER     = new DeviceRgb(226, 232, 240);

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_FMT = DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH);

    public byte[] generateStatementPdf(TenantId tenantId, UUID customerId, LocalDate from, LocalDate to) {
        var statement = statementService.buildStatement(tenantId, customerId, from, to);
        String companyName = tenantFacade.findTenantDetails(tenantId).map(t -> t.companyName()).orElse("");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(36, 40, 36, 40);

            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            addHeader(document, companyName, statement, bold, regular);
            addAgingSummary(document, statement, bold, regular);
            addInvoiceTable(document, statement, bold, regular);
            addFooter(document, regular);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate statement of account PDF for customer={}: {}", customerId, e.getMessage(), e);
            throw new RuntimeException("Statement of account PDF generation failed", e);
        }
    }

    private void addHeader(Document doc, String companyName, StatementOfAccountService.CustomerStatement s,
                           PdfFont bold, PdfFont regular) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
        header.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(14)));
        header.addCell(cellOf(new Paragraph("STATEMENT OF ACCOUNT").setFont(bold).setFontSize(18)
                .setFontColor(TEAL).setTextAlignment(TextAlignment.RIGHT)));
        doc.add(header);
        doc.add(new LineSeparator(new SolidLine(1f)).setMarginTop(8).setMarginBottom(14).setFontColor(TEAL));

        Table info = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth().setMarginBottom(16);
        info.addCell(cellOf(new Paragraph("Customer: " + s.customerName()).setFont(regular).setFontSize(10)));
        info.addCell(cellOf(new Paragraph("Generated: " + LocalDate.now().format(DATE_FMT))
                .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        String periodStr = (s.periodFrom() != null ? s.periodFrom().format(SHORT_FMT) : "All time")
                + " – " + (s.periodTo() != null ? s.periodTo().format(SHORT_FMT) : "present");
        info.addCell(cellOf(new Paragraph("Period: " + periodStr).setFont(regular).setFontSize(10)));
        info.addCell(cellOf(new Paragraph(s.customerEmail() != null ? s.customerEmail() : "")
                .setFont(regular).setFontSize(10).setFontColor(TEXT_MUTED).setTextAlignment(TextAlignment.RIGHT)));
        doc.add(info);

        Table amountBox = new Table(1).useAllAvailableWidth().setBackgroundColor(BG_LIGHT).setPadding(14).setMarginBottom(16);
        amountBox.addCell(cellOf(new Paragraph("TOTAL OUTSTANDING").setFont(bold).setFontSize(9).setFontColor(TEAL)));
        amountBox.addCell(cellOf(new Paragraph(zar(s.totalOutstanding())).setFont(bold).setFontSize(24)
                .setFontColor(BRAND_DARK).setMarginTop(2)));
        doc.add(amountBox);
    }

    private void addAgingSummary(Document doc, StatementOfAccountService.CustomerStatement s, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("AGING SUMMARY").setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginBottom(6));
        Table aging = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1, 1})).useAllAvailableWidth().setMarginBottom(16);
        Object[][] buckets = {
                {"Current", s.current(), false}, {"1-30 days", s.days1to30(), true},
                {"31-60 days", s.days31to60(), true}, {"61-90 days", s.days61to90(), true},
                {"90+ days", s.days90plus(), true}
        };
        for (Object[] b : buckets) {
            BigDecimal amount = (BigDecimal) b[1];
            boolean overdueBucket = (boolean) b[2];
            Cell c = new Cell().setBorder(new SolidBorder(BORDER, 0.5f)).setPadding(8);
            c.add(new Paragraph((String) b[0]).setFont(regular).setFontSize(8).setFontColor(TEXT_MUTED));
            c.add(new Paragraph(zar(amount)).setFont(bold).setFontSize(11)
                    .setFontColor(overdueBucket && amount.compareTo(BigDecimal.ZERO) > 0 ? RED : BRAND_DARK));
            aging.addCell(c);
        }
        doc.add(aging);
    }

    private void addInvoiceTable(Document doc, StatementOfAccountService.CustomerStatement s, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("INVOICES").setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginBottom(6));
        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 1.4f, 1.4f, 1.2f, 1.2f, 1.2f, 1.4f})).useAllAvailableWidth();
        for (String h : new String[]{"Invoice", "Issued", "Due", "Total", "Paid", "Credited", "Balance"}) {
            table.addHeaderCell(headerCell(h, bold));
        }
        for (var line : s.lines()) {
            boolean overdue = line.daysOverdue() > 0 && line.balance().compareTo(BigDecimal.ZERO) > 0;
            table.addCell(cellOf(new Paragraph(line.invoiceNumber()).setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(line.issuedDate().format(SHORT_FMT)).setFont(regular).setFontSize(9)));
            Paragraph dueDatePara = new Paragraph(line.dueDate() != null
                    ? line.dueDate().format(SHORT_FMT) + (line.dueDateEstimated() ? " (est.)" : "")
                    : "—").setFont(regular).setFontSize(9);
            if (line.dueDateEstimated()) dueDatePara.setFontColor(TEXT_MUTED);
            table.addCell(cellOf(dueDatePara));
            table.addCell(cellOf(new Paragraph(zar(line.total())).setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(zar(line.amountPaid())).setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(zar(line.creditedTotal())).setFont(regular).setFontSize(9)));
            table.addCell(cellOf(new Paragraph(zar(line.balance())).setFont(bold).setFontSize(9).setFontColor(overdue ? RED : BRAND_DARK)));
        }
        if (s.lines().isEmpty()) {
            Cell empty = new Cell(1, 7).setBorder(Border.NO_BORDER).setPadding(12);
            empty.add(new Paragraph("No issued invoices for this customer in this period.").setFont(regular).setFontSize(10).setFontColor(TEXT_MUTED));
            table.addCell(empty);
        }
        doc.add(table);

        Table totals = new Table(UnitValue.createPercentArray(new float[]{2, 1})).useAllAvailableWidth().setMarginTop(12);
        addTotalRow(totals, "Total billed", zar(s.totalBilled()), regular);
        addTotalRow(totals, "Total paid", zar(s.totalPaid()), regular);
        addTotalRow(totals, "Total credited", zar(s.totalCredited()), regular);
        addTotalRow(totals, "Total outstanding", zar(s.totalOutstanding()), bold);
        doc.add(totals);
    }

    private void addTotalRow(Table t, String label, String value, PdfFont font) {
        t.addCell(cellOf(new Paragraph(label).setFont(font).setFontSize(10)));
        t.addCell(cellOf(new Paragraph(value).setFont(font).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
    }

    private void addFooter(Document doc, PdfFont regular) {
        doc.add(new Paragraph("(est.) — no due date was set on this invoice; the date shown is derived from your account's payment terms and used for aging.")
                .setFont(regular).setFontSize(7).setFontColor(TEXT_MUTED).setMarginTop(10));
        doc.add(new Paragraph("Please remit payment for any outstanding balance at your earliest convenience. "
                + "Contact us with any queries about this statement.")
                .setFont(regular).setFontSize(8).setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(10));
    }

    private Cell cellOf(IBlockElement content) {
        Cell c = new Cell();
        c.add(content);
        c.setBorder(Border.NO_BORDER);
        return c;
    }

    private Cell headerCell(String text, PdfFont bold) {
        Cell c = new Cell();
        c.add(new Paragraph(text).setFont(bold).setFontSize(8).setFontColor(WHITE));
        c.setBackgroundColor(BRAND_DARK);
        c.setBorder(Border.NO_BORDER);
        return c;
    }

    private String zar(BigDecimal amount) {
        return "R " + String.format(Locale.US, "%,.2f", amount != null ? amount : BigDecimal.ZERO);
    }
}