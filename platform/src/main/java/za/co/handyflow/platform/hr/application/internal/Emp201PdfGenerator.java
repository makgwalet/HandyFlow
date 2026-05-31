package za.co.handyflow.platform.hr.application.internal;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.HrEmp201;
import za.co.handyflow.platform.hr.domain.model.HrPayRun;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
public class Emp201PdfGenerator {

    private static final DeviceRgb BRAND_NAVY  = new DeviceRgb(27,  58,  107);
    private static final DeviceRgb BRAND_TEAL  = new DeviceRgb(13,  148, 136);
    private static final DeviceRgb TEAL_LIGHT  = new DeviceRgb(225, 245, 238);
    private static final DeviceRgb LIGHT_GRAY  = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb MID_GRAY    = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb DARK_TEXT   = new DeviceRgb(15,  23,  42);
    private static final DeviceRgb WARN_BG     = new DeviceRgb(254, 243, 199);
    private static final DeviceRgb WARN_FG     = new DeviceRgb(146, 64,  14);
    private static final DeviceRgb SUCCESS_BG  = new DeviceRgb(220, 252, 231);
    private static final DeviceRgb SUCCESS_FG  = new DeviceRgb(22,  101, 52);
    private static final DeviceRgb ROW_ALT     = new DeviceRgb(241, 245, 249);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("dd MMMM yyyy")
            .withZone(ZoneId.of("Africa/Johannesburg"));
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter
            .ofPattern("MMMM yyyy");
    private static final NumberFormat ZAR = NumberFormat.getInstance(new Locale("en", "ZA"));

    static {
        ZAR.setMinimumFractionDigits(2);
        ZAR.setMaximumFractionDigits(2);
    }

    public byte[] generate(HrEmp201 emp201, HrPayRun payRun,
                            String tenantName, String tenantVat,
                            int employeeCount) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PdfWriter   writer  = new PdfWriter(bos);
            PdfDocument pdfDoc  = new PdfDocument(writer);
            Document    doc     = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(36, 36, 36, 36);

            String period = MONTH_FMT.format(emp201.getPeriodEnd());

            // ── Header band ───────────────────────────────────────────────────
            Table header = new Table(UnitValue.createPercentArray(new float[]{70, 30}))
                    .setWidth(UnitValue.createPercentValue(100));

            Cell left = new Cell().setBorder(null)
                    .setBackgroundColor(BRAND_NAVY)
                    .setPadding(16);
            left.add(new Paragraph("EMP201")
                    .setFontSize(28).setBold()
                    .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE));
            left.add(new Paragraph("Monthly Employer Declaration")
                    .setFontSize(11)
                    .setFontColor(BRAND_TEAL));
            left.add(new Paragraph("South African Revenue Service")
                    .setFontSize(9)
                    .setFontColor(new DeviceRgb(148, 163, 184)));
            header.addCell(left);

            Cell right = new Cell().setBorder(null)
                    .setBackgroundColor(BRAND_TEAL)
                    .setPadding(16)
                    .setTextAlignment(TextAlignment.RIGHT);
            right.add(new Paragraph("Declaration Period")
                    .setFontSize(9).setBold()
                    .setFontColor(TEAL_LIGHT));
            right.add(new Paragraph(period)
                    .setFontSize(16).setBold()
                    .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE));
            right.add(new Paragraph("Due: " + DATE_FMT.format(emp201.getDueDate().atStartOfDay(ZoneId.of("Africa/Johannesburg")).toInstant()))
                    .setFontSize(9)
                    .setFontColor(TEAL_LIGHT));
            header.addCell(right);
            doc.add(header);

            // ── Status badge ──────────────────────────────────────────────────
            DeviceRgb statusBg  = "SUBMITTED".equals(emp201.getStatus()) ? SUCCESS_BG
                    : "PAID".equals(emp201.getStatus()) ? SUCCESS_BG : WARN_BG;
            DeviceRgb statusFg  = "SUBMITTED".equals(emp201.getStatus()) ? SUCCESS_FG
                    : "PAID".equals(emp201.getStatus()) ? SUCCESS_FG : WARN_FG;
            String statusLabel  = switch (emp201.getStatus()) {
                case "SUBMITTED" -> "SUBMITTED TO SARS";
                case "PAID"      -> "PAYMENT CONFIRMED";
                default          -> "DRAFT — NOT YET SUBMITTED";
            };

            doc.add(new Paragraph(statusLabel)
                    .setFontSize(9).setBold()
                    .setFontColor(statusFg)
                    .setBackgroundColor(statusBg)
                    .setPadding(6)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(0).setMarginBottom(14));

            // ── Employer details ──────────────────────────────────────────────
            doc.add(sectionHeading("Employer Details"));
            Table empDetails = twoColTable();
            addDetail(empDetails, "Employer / Trading Name", tenantName, true);
            addDetail(empDetails, "VAT Registration Number",
                    tenantVat != null ? tenantVat : "Not registered", false);
            addDetail(empDetails, "Payroll Reference Number",
                    payRun != null ? payRun.getPayRunNumber() : "—", true);
            addDetail(empDetails, "Number of Employees", String.valueOf(employeeCount), false);
            addDetail(empDetails, "Period Start",
                    DATE_FMT.format(emp201.getPeriodStart().atStartOfDay(
                            ZoneId.of("Africa/Johannesburg")).toInstant()), true);
            addDetail(empDetails, "Period End",
                    DATE_FMT.format(emp201.getPeriodEnd().atStartOfDay(
                            ZoneId.of("Africa/Johannesburg")).toInstant()), false);
            doc.add(empDetails);
            doc.add(spacer(10));

            // ── Levy breakdown ────────────────────────────────────────────────
            doc.add(sectionHeading("Monthly Levy Breakdown"));

            Table levies = new Table(UnitValue.createPercentArray(new float[]{50, 30, 20}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginBottom(4);

            // Header row
            for (String h : new String[]{"Levy Type", "Description", "Amount (ZAR)"}) {
                levies.addHeaderCell(new Cell()
                        .setBackgroundColor(BRAND_NAVY)
                        .setBorder(null)
                        .setPadding(8)
                        .add(new Paragraph(h)
                                .setFontSize(9).setBold()
                                .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)));
            }

            // PAYE row
            addLevyRow(levies, "PAYE",
                    "Pay-As-You-Earn income tax withheld from employee salaries",
                    emp201.getTotalPaye(), false);

            // UIF row
            addLevyRow(levies, "UIF",
                    "Unemployment Insurance Fund — 1% employer + 1% employee (capped at R177.12)",
                    emp201.getTotalUif(), true);

            // SDL row
            addLevyRow(levies, "SDL",
                    "Skills Development Levy — 1% of gross payroll (if annual payroll > R500,000)",
                    emp201.getTotalSdl(), false);

            doc.add(levies);

            // ── Total payable ─────────────────────────────────────────────────
            Table totalTable = new Table(UnitValue.createPercentArray(new float[]{50, 30, 20}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginBottom(16);
            totalTable.addCell(new Cell(1, 2)
                    .setBackgroundColor(BRAND_NAVY)
                    .setBorder(null).setPadding(10)
                    .add(new Paragraph("TOTAL PAYABLE TO SARS")
                            .setFontSize(11).setBold()
                            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)));
            totalTable.addCell(new Cell()
                    .setBackgroundColor(BRAND_TEAL)
                    .setBorder(null).setPadding(10)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph("R " + fmt(emp201.getTotalPayable()))
                            .setFontSize(13).setBold()
                            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)));
            doc.add(totalTable);

            // ── Payment reference ─────────────────────────────────────────────
            if (emp201.getPaymentRef() != null) {
                doc.add(sectionHeading("Payment Reference"));
                Table payRef = twoColTable();
                addDetail(payRef, "Payment Reference", emp201.getPaymentRef(), false);
                if (emp201.getSubmittedAt() != null) {
                    addDetail(payRef, "Submitted At",
                            DATE_FMT.format(emp201.getSubmittedAt()), true);
                }
                doc.add(payRef);
                doc.add(spacer(10));
            }

            // ── SARS payment instructions ─────────────────────────────────────
            doc.add(sectionHeading("Payment Instructions"));
            Table instructions = new Table(UnitValue.createPercentArray(new float[]{35, 65}))
                    .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(16);
            String[][] rows = {
                {"Payment Method",  "eFiling (recommended), SARS branch, or EFT"},
                {"Bank",            "ABSA Bank"},
                {"Account Number",  "4048685083"},
                {"Branch Code",     "632005"},
                {"Account Name",    "South African Revenue Service"},
                {"Payment Ref",     "Use your PAYE reference number as the payment reference"},
                {"Due Date",        DATE_FMT.format(emp201.getDueDate().atStartOfDay(
                                        ZoneId.of("Africa/Johannesburg")).toInstant())
                                    + " (7th of the following month)"},
            };
            for (int i = 0; i < rows.length; i++) {
                boolean alt = i % 2 == 0;
                rows[i][0] = rows[i][0]; // keep reference
                instructions.addCell(new Cell()
                        .setBackgroundColor(alt ? LIGHT_GRAY : new DeviceRgb(255, 255, 255))
                        .setBorder(new SolidBorder(new DeviceRgb(226, 232, 240), 0.5f))
                        .setPadding(7)
                        .add(new Paragraph(rows[i][0])
                                .setFontSize(9).setBold().setFontColor(DARK_TEXT)));
                instructions.addCell(new Cell()
                        .setBackgroundColor(alt ? LIGHT_GRAY : new DeviceRgb(255, 255, 255))
                        .setBorder(new SolidBorder(new DeviceRgb(226, 232, 240), 0.5f))
                        .setPadding(7)
                        .add(new Paragraph(rows[i][1])
                                .setFontSize(9).setFontColor(DARK_TEXT)));
            }
            doc.add(instructions);

            // ── Disclaimer ────────────────────────────────────────────────────
            doc.add(new Paragraph(
                    "This EMP201 declaration was generated by HandyFlow. " +
                    "Employers must submit this declaration via SARS eFiling or at a SARS branch. " +
                    "Late submissions attract penalties and interest. " +
                    "Ensure your PAYE reference number appears on all payments.")
                    .setFontSize(8)
                    .setFontColor(MID_GRAY)
                    .setBackgroundColor(LIGHT_GRAY)
                    .setPadding(8)
                    .setTextAlignment(TextAlignment.CENTER));

            // ── Footer ────────────────────────────────────────────────────────
            doc.add(new Paragraph("Generated by HandyFlow · " +
                    DATE_FMT.format(java.time.Instant.now()) +
                    " · For official submission use SARS eFiling")
                    .setFontSize(7)
                    .setFontColor(MID_GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(10));

            doc.close();
            return bos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate EMP201 PDF: {}", e.getMessage(), e);
            throw new RuntimeException("EMP201 PDF generation failed", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Paragraph sectionHeading(String text) {
        return new Paragraph(text)
                .setFontSize(11).setBold()
                .setFontColor(BRAND_NAVY)
                .setMarginTop(10).setMarginBottom(6)
                .setBorderBottom(new SolidBorder(BRAND_TEAL, 1.5f));
    }

    private Table twoColTable() {
        return new Table(UnitValue.createPercentArray(new float[]{40, 60}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(4);
    }

    private void addDetail(Table table, String label, String value, boolean alt) {
        DeviceRgb bg = alt ? LIGHT_GRAY : new DeviceRgb(255, 255, 255);
        table.addCell(new Cell()
                .setBackgroundColor(bg)
                .setBorder(new SolidBorder(new DeviceRgb(226, 232, 240), 0.5f))
                .setPadding(7)
                .add(new Paragraph(label).setFontSize(9).setBold().setFontColor(MID_GRAY)));
        table.addCell(new Cell()
                .setBackgroundColor(bg)
                .setBorder(new SolidBorder(new DeviceRgb(226, 232, 240), 0.5f))
                .setPadding(7)
                .add(new Paragraph(value).setFontSize(9).setFontColor(DARK_TEXT)));
    }

    private void addLevyRow(Table table, String type, String description,
                             BigDecimal amount, boolean alt) {
        DeviceRgb bg = alt ? ROW_ALT : new DeviceRgb(255, 255, 255);
        table.addCell(new Cell()
                .setBackgroundColor(bg)
                .setBorder(new SolidBorder(new DeviceRgb(226, 232, 240), 0.5f))
                .setPadding(8)
                .add(new Paragraph(type).setFontSize(10).setBold().setFontColor(BRAND_NAVY)));
        table.addCell(new Cell()
                .setBackgroundColor(bg)
                .setBorder(new SolidBorder(new DeviceRgb(226, 232, 240), 0.5f))
                .setPadding(8)
                .add(new Paragraph(description).setFontSize(8).setFontColor(MID_GRAY)));
        table.addCell(new Cell()
                .setBackgroundColor(bg)
                .setBorder(new SolidBorder(new DeviceRgb(226, 232, 240), 0.5f))
                .setPadding(8)
                .setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph("R " + fmt(amount))
                        .setFontSize(10).setBold().setFontColor(DARK_TEXT)));
    }

    private Paragraph spacer(float height) {
        return new Paragraph(" ").setFontSize(1).setMarginTop(height);
    }

    private String fmt(BigDecimal val) {
        if (val == null) return "0.00";
        return ZAR.format(val);
    }
}
