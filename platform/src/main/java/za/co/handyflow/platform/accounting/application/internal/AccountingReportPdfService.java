package za.co.handyflow.platform.accounting.application.internal;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.accounting.dto.FinancialReportResponse;
import za.co.handyflow.platform.accounting.dto.Vat201Response;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Generates PDF exports for financial reports:
 * Profit & Loss, Balance Sheet, Trial Balance, VAT201 summary.
 *
 * WHY a separate service?
 * AccountingService is already large. PDF generation is a distinct concern
 * (layout, fonts, iText API) — keeping it separate follows SRP and makes
 * the PDF logic independently testable and replaceable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingReportPdfService {

    private final AccountingService accountingService;
    private final TenantFacade      tenantFacade;

    private static final DeviceRgb NAVY      = new DeviceRgb(0x1B, 0x3A, 0x6B);
    private static final DeviceRgb TEAL      = new DeviceRgb(0x0D, 0x94, 0x88);
    private static final DeviceRgb WHITE     = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb LIGHT_BG  = new DeviceRgb(0xF8, 0xFA, 0xFC);
    private static final DeviceRgb MID_GRAY  = new DeviceRgb(0xE2, 0xE8, 0xF0);
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(0x0F, 0x17, 0x2A);
    private static final DeviceRgb TEXT_GRAY = new DeviceRgb(0x64, 0x74, 0x8B);
    private static final DeviceRgb RED       = new DeviceRgb(0xDC, 0x26, 0x26);
    private static final DeviceRgb GREEN     = new DeviceRgb(0x16, 0x65, 0x34);
    private static final DeviceRgb AMBER     = new DeviceRgb(0xD9, 0x77, 0x06);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy");

    // ── Public entry points ───────────────────────────────────────────────────

    public byte[] generateProfitAndLoss(TenantId tenantId, LocalDate from, LocalDate to) {
        TenantDetails tenant = resolveTenant(tenantId);
        FinancialReportResponse report = accountingService.getProfitAndLoss(tenantId, from, to);
        return buildFinancialPdf(tenant, report, from, to, TEAL);
    }

    public byte[] generateBalanceSheet(TenantId tenantId, LocalDate from, LocalDate to) {
        TenantDetails tenant = resolveTenant(tenantId);
        FinancialReportResponse report = accountingService.getBalanceSheet(tenantId, from, to);
        return buildFinancialPdf(tenant, report, from, to, NAVY);
    }

    public byte[] generateTrialBalance(TenantId tenantId, LocalDate from, LocalDate to) {
        TenantDetails tenant = resolveTenant(tenantId);
        FinancialReportResponse report = accountingService.getTrialBalance(tenantId, from, to);
        return buildTrialBalancePdf(tenant, report, from, to);
    }

    public byte[] generateVat201(TenantId tenantId, LocalDate from, LocalDate to) {
        TenantDetails tenant = resolveTenant(tenantId);
        Vat201Response vat = accountingService.getVat201(tenantId, from, to);
        return buildVat201Pdf(tenant, vat);
    }

    // ── Font helper ───────────────────────────────────────────────────────────

    private PdfFont font(String name) {
        try {
            return PdfFontFactory.createFont(
                    Objects.requireNonNull(
                            AccountingReportPdfService.class.getResourceAsStream("/fonts/" + name)
                    ).readAllBytes(),
                    PdfEncodings.WINANSI, EmbeddingStrategy.FORCE_EMBEDDED);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load font: " + name, e);
        }
    }

    // ── Profit & Loss / Balance Sheet builder ─────────────────────────────────

    private byte[] buildFinancialPdf(TenantDetails tenant, FinancialReportResponse report,
                                     LocalDate from, LocalDate to, DeviceRgb accentColor) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 50, 50, 50);

            PdfFont regular = font("LiberationSans-Regular.ttf");
            PdfFont bold    = font("LiberationSans-Bold.ttf");

            // ── Header bar ────────────────────────────────────────────────────
            Table header = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                    .useAllAvailableWidth().setMarginBottom(24);

            Cell left = new Cell().setBorder(Border.NO_BORDER).setPadding(16)
                    .setBackgroundColor(accentColor);
            left.add(new Paragraph(reportTitle(report.reportType()))
                    .setFont(bold).setFontSize(20).setFontColor(WHITE).setMarginBottom(4));
            left.add(new Paragraph(tenant.companyName())
                    .setFont(regular).setFontSize(10).setFontColor(WHITE));

            Cell right = new Cell().setBorder(Border.NO_BORDER).setPadding(16)
                    .setBackgroundColor(LIGHT_BG).setTextAlignment(TextAlignment.RIGHT);
            right.add(new Paragraph("Period")
                    .setFont(bold).setFontSize(8).setFontColor(TEXT_GRAY)
                    .setCharacterSpacing(0.5f).setMarginBottom(4));
            right.add(new Paragraph(from.format(DATE_FMT) + " – " + to.format(DATE_FMT))
                    .setFont(regular).setFontSize(9).setFontColor(TEXT_DARK).setMarginBottom(8));
            if (tenant.vatNumber() != null) {
                right.add(new Paragraph("VAT Reg: " + tenant.vatNumber())
                        .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY));
            }
            header.addCell(left);
            header.addCell(right);
            doc.add(header);

            // ── Sections ──────────────────────────────────────────────────────
            for (FinancialReportResponse.ReportSection section : report.sections()) {
                // Section heading
                doc.add(new Paragraph(section.title().toUpperCase())
                        .setFont(bold).setFontSize(8).setFontColor(accentColor)
                        .setCharacterSpacing(1f).setMarginBottom(6).setMarginTop(16));

                // Lines table
                Table t = new Table(UnitValue.createPercentArray(new float[]{10, 60, 30}))
                        .useAllAvailableWidth().setMarginBottom(4);

                for (int i = 0; i < section.lines().size(); i++) {
                    FinancialReportResponse.ReportLine line = section.lines().get(i);
                    boolean even = i % 2 == 0;
                    DeviceRgb bg = even ? LIGHT_BG : WHITE;

                    t.addCell(new Cell().setBackgroundColor(bg).setBorder(Border.NO_BORDER)
                            .setPadding(7)
                            .add(new Paragraph(line.accountCode())
                                    .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY)));
                    t.addCell(new Cell().setBackgroundColor(bg).setBorder(Border.NO_BORDER)
                            .setPadding(7)
                            .add(new Paragraph(line.accountName())
                                    .setFont(regular).setFontSize(9).setFontColor(TEXT_DARK)));
                    t.addCell(new Cell().setBackgroundColor(bg).setBorder(Border.NO_BORDER)
                            .setPadding(7).setTextAlignment(TextAlignment.RIGHT)
                            .add(new Paragraph(fmtR(line.amount()))
                                    .setFont(bold).setFontSize(9).setFontColor(TEXT_DARK)));
                }
                doc.add(t);

                // Section total
                Table total = new Table(UnitValue.createPercentArray(new float[]{70, 30}))
                        .useAllAvailableWidth().setMarginBottom(8);
                total.addCell(new Cell().setBorder(Border.NO_BORDER)
                        .setBorderTop(new SolidBorder(MID_GRAY, 1)).setPadding(8)
                        .add(new Paragraph("Total " + section.title())
                                .setFont(bold).setFontSize(9).setFontColor(TEXT_DARK)));
                total.addCell(new Cell().setBorder(Border.NO_BORDER)
                        .setBorderTop(new SolidBorder(MID_GRAY, 1))
                        .setPadding(8).setTextAlignment(TextAlignment.RIGHT)
                        .add(new Paragraph(fmtR(section.total()))
                                .setFont(bold).setFontSize(10).setFontColor(accentColor)));
                doc.add(total);
            }

            // ── Net result bar ────────────────────────────────────────────────
            String netLabel = "PROFIT_AND_LOSS".equals(report.reportType())
                    ? (report.netResult().compareTo(BigDecimal.ZERO) >= 0 ? "NET PROFIT" : "NET LOSS")
                    : "NET POSITION";
            boolean positive = report.netResult().compareTo(BigDecimal.ZERO) >= 0;
            DeviceRgb netColor = positive ? GREEN : RED;

            Table net = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                    .useAllAvailableWidth().setMarginTop(16);
            net.addCell(new Cell().setBackgroundColor(NAVY).setBorder(Border.NO_BORDER)
                    .setPadding(14)
                    .add(new Paragraph(netLabel)
                            .setFont(bold).setFontSize(11).setFontColor(WHITE)));
            net.addCell(new Cell().setBackgroundColor(NAVY).setBorder(Border.NO_BORDER)
                    .setPadding(14).setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph(fmtR(report.netResult()))
                            .setFont(bold).setFontSize(14).setFontColor(netColor)));
            doc.add(net);

            footer(doc, tenant, regular);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate financial report PDF", e);
        }
    }

    // ── Trial Balance builder ─────────────────────────────────────────────────

    private byte[] buildTrialBalancePdf(TenantDetails tenant, FinancialReportResponse report,
                                        LocalDate from, LocalDate to) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 50, 50, 50);

            PdfFont regular = font("LiberationSans-Regular.ttf");
            PdfFont bold    = font("LiberationSans-Bold.ttf");

            // Header
            Table header = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                    .useAllAvailableWidth().setMarginBottom(24);
            Cell left = new Cell().setBorder(Border.NO_BORDER).setPadding(16)
                    .setBackgroundColor(AMBER);
            left.add(new Paragraph("TRIAL BALANCE")
                    .setFont(bold).setFontSize(20).setFontColor(WHITE).setMarginBottom(4));
            left.add(new Paragraph(tenant.companyName())
                    .setFont(regular).setFontSize(10).setFontColor(WHITE));
            Cell right = new Cell().setBorder(Border.NO_BORDER).setPadding(16)
                    .setBackgroundColor(LIGHT_BG).setTextAlignment(TextAlignment.RIGHT);
            right.add(new Paragraph("Period")
                    .setFont(bold).setFontSize(8).setFontColor(TEXT_GRAY)
                    .setCharacterSpacing(0.5f).setMarginBottom(4));
            right.add(new Paragraph(from.format(DATE_FMT) + " – " + to.format(DATE_FMT))
                    .setFont(regular).setFontSize(9).setFontColor(TEXT_DARK));
            header.addCell(left);
            header.addCell(right);
            doc.add(header);

            // Column headers
            Table t = new Table(UnitValue.createPercentArray(new float[]{8, 42, 25, 25}))
                    .useAllAvailableWidth();
            for (String h : List.of("Code", "Account", "Debit", "Credit")) {
                t.addCell(new Cell().setBackgroundColor(NAVY).setBorder(Border.NO_BORDER)
                        .setPadding(8)
                        .add(new Paragraph(h).setFont(bold).setFontSize(8)
                                .setFontColor(WHITE)
                                .setTextAlignment("Code".equals(h) || "Account".equals(h)
                                        ? TextAlignment.LEFT : TextAlignment.RIGHT)));
            }

            // Lines
            BigDecimal totalDebit = BigDecimal.ZERO, totalCredit = BigDecimal.ZERO;
            List<FinancialReportResponse.ReportLine> lines =
                    report.sections().isEmpty() ? List.of()
                            : report.sections().get(0).lines();

            for (int i = 0; i < lines.size(); i++) {
                FinancialReportResponse.ReportLine line = lines.get(i);
                DeviceRgb bg = i % 2 == 0 ? LIGHT_BG : WHITE;
                BigDecimal dr = line.grossDebit()  != null ? line.grossDebit()  : BigDecimal.ZERO;
                BigDecimal cr = line.grossCredit() != null ? line.grossCredit() : BigDecimal.ZERO;
                totalDebit  = totalDebit.add(dr);
                totalCredit = totalCredit.add(cr);

                t.addCell(rowCell(line.accountCode(), bg, regular, TextAlignment.LEFT));
                t.addCell(rowCell(line.accountName(), bg, regular, TextAlignment.LEFT));
                t.addCell(rowCell(dr.compareTo(BigDecimal.ZERO) > 0 ? fmtR(dr) : "", bg, regular, TextAlignment.RIGHT));
                t.addCell(rowCell(cr.compareTo(BigDecimal.ZERO) > 0 ? fmtR(cr) : "", bg, regular, TextAlignment.RIGHT));
            }
            doc.add(t);

            // Totals row
            Table totals = new Table(UnitValue.createPercentArray(new float[]{50, 25, 25}))
                    .useAllAvailableWidth().setMarginTop(4);
            totals.addCell(new Cell().setBackgroundColor(NAVY).setBorder(Border.NO_BORDER)
                    .setPadding(10)
                    .add(new Paragraph("TOTALS").setFont(bold).setFontSize(9).setFontColor(WHITE)));
            totals.addCell(new Cell().setBackgroundColor(NAVY).setBorder(Border.NO_BORDER)
                    .setPadding(10).setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph(fmtR(totalDebit)).setFont(bold).setFontSize(9).setFontColor(WHITE)));
            totals.addCell(new Cell().setBackgroundColor(NAVY).setBorder(Border.NO_BORDER)
                    .setPadding(10).setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph(fmtR(totalCredit)).setFont(bold).setFontSize(9).setFontColor(WHITE)));
            doc.add(totals);

            footer(doc, tenant, regular);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate trial balance PDF", e);
        }
    }

    // ── VAT201 builder ────────────────────────────────────────────────────────

    private byte[] buildVat201Pdf(TenantDetails tenant, Vat201Response vat) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 50, 50, 50);

            PdfFont regular = font("LiberationSans-Regular.ttf");
            PdfFont bold    = font("LiberationSans-Bold.ttf");

            // Header
            Table header = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                    .useAllAvailableWidth().setMarginBottom(32);
            Cell left = new Cell().setBorder(Border.NO_BORDER).setPadding(16)
                    .setBackgroundColor(new DeviceRgb(0x7C, 0x3A, 0xED));
            left.add(new Paragraph("VAT RETURN — VAT201")
                    .setFont(bold).setFontSize(18).setFontColor(WHITE).setMarginBottom(4));
            left.add(new Paragraph(tenant.companyName())
                    .setFont(regular).setFontSize(10).setFontColor(WHITE));
            Cell right = new Cell().setBorder(Border.NO_BORDER).setPadding(16)
                    .setBackgroundColor(LIGHT_BG).setTextAlignment(TextAlignment.RIGHT);
            right.add(new Paragraph("Tax Period")
                    .setFont(bold).setFontSize(8).setFontColor(TEXT_GRAY)
                    .setCharacterSpacing(0.5f).setMarginBottom(4));
            right.add(new Paragraph(vat.from().format(DATE_FMT) + " to " + vat.to().format(DATE_FMT))
                    .setFont(regular).setFontSize(9).setFontColor(TEXT_DARK).setMarginBottom(6));
            if (tenant.vatNumber() != null) {
                right.add(new Paragraph("VAT Reg: " + tenant.vatNumber())
                        .setFont(bold).setFontSize(9).setFontColor(NAVY));
            }
            header.addCell(left);
            header.addCell(right);
            doc.add(header);

            // VAT boxes
            record VatBox(String box, String label, String desc, BigDecimal value, DeviceRgb color) {}
            DeviceRgb purple = new DeviceRgb(0x7C, 0x3A, 0xED);
            List<VatBox> boxes = List.of(
                    new VatBox("Box 1",  "Total Sales (excl. VAT)",    vat.invoiceCount() + " invoices",
                            vat.totalSales(),   TEAL),
                    new VatBox("Box 4",  "Output VAT",                  "VAT charged on sales",
                            vat.outputVat(),    NAVY),
                    new VatBox("Box 15", "Input VAT",                   "VAT claimable on purchases",
                            vat.inputVat(),     purple),
                    new VatBox("Box 17", "Net VAT Payable / Refundable",
                            vat.netVatPayable().compareTo(BigDecimal.ZERO) >= 0
                                    ? "Payable to SARS" : "Refund due from SARS",
                            vat.netVatPayable(),
                            vat.netVatPayable().compareTo(BigDecimal.ZERO) >= 0 ? RED : GREEN)
            );

            for (VatBox box : boxes) {
                Table row = new Table(UnitValue.createPercentArray(new float[]{15, 55, 30}))
                        .useAllAvailableWidth().setMarginBottom(8);
                row.addCell(new Cell().setBackgroundColor(box.color()).setBorder(Border.NO_BORDER)
                        .setPadding(14).setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .add(new Paragraph(box.box()).setFont(bold).setFontSize(9).setFontColor(WHITE)));
                row.addCell(new Cell().setBackgroundColor(LIGHT_BG).setBorder(Border.NO_BORDER)
                        .setPadding(14).setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .add(new Paragraph(box.label()).setFont(bold).setFontSize(10).setFontColor(TEXT_DARK).setMarginBottom(3))
                        .add(new Paragraph(box.desc()).setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY)));
                row.addCell(new Cell().setBackgroundColor(LIGHT_BG).setBorder(Border.NO_BORDER)
                        .setBorderLeft(new SolidBorder(MID_GRAY, 1))
                        .setPadding(14).setTextAlignment(TextAlignment.RIGHT)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .add(new Paragraph(fmtR(box.value()))
                                .setFont(bold).setFontSize(14).setFontColor(box.color())));
                doc.add(row);
            }

            // Disclaimer
            doc.add(new Paragraph(
                    "\nThis VAT201 summary is generated from posted journal entries in HandyFlow. "
                            + "It is for reference purposes. Please verify figures with your accountant "
                            + "before submitting to SARS.")
                    .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY).setMarginTop(24));

            footer(doc, tenant, regular);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate VAT201 PDF", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void footer(Document doc, TenantDetails tenant, PdfFont regular) {
        doc.add(new Paragraph(
                "Generated by HandyFlow Business Operating System  •  " + tenant.companyName()
                        + "  •  " + LocalDate.now().format(DATE_FMT))
                .setFont(regular).setFontSize(7).setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(32));
    }

    private Cell rowCell(String text, DeviceRgb bg, PdfFont font, TextAlignment align) {
        return new Cell().setBackgroundColor(bg).setBorder(Border.NO_BORDER).setPadding(7)
                .setTextAlignment(align)
                .add(new Paragraph(text != null ? text : "").setFont(font).setFontSize(9).setFontColor(TEXT_DARK));
    }

    private String fmtR(BigDecimal v) {
        if (v == null) return "R 0.00";
        return "R " + String.format("%,.2f", v.abs());
    }

    private String reportTitle(String type) {
        return switch (type) {
            case "PROFIT_AND_LOSS" -> "PROFIT & LOSS";
            case "BALANCE_SHEET"   -> "BALANCE SHEET";
            case "TRIAL_BALANCE"   -> "TRIAL BALANCE";
            default -> type.replace("_", " ");
        };
    }

    private TenantDetails resolveTenant(TenantId tenantId) {
        return tenantFacade.findTenantDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.toString()));
    }
}
