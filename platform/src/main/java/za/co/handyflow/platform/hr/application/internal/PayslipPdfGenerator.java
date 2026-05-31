package za.co.handyflow.platform.hr.application.internal;

import com.itextpdf.kernel.colors.ColorConstants;
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
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrPayRun;
import za.co.handyflow.platform.hr.domain.model.HrPayslip;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
public class PayslipPdfGenerator {

    private static final DeviceRgb BRAND_NAVY  = new DeviceRgb(27,  58,  107);
    private static final DeviceRgb BRAND_TEAL  = new DeviceRgb(13,  148, 136);
    private static final DeviceRgb TEAL_LIGHT  = new DeviceRgb(225, 245, 238);
    private static final DeviceRgb LIGHT_GRAY  = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb MID_GRAY    = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb DARK_TEXT   = new DeviceRgb(15,  23,  42);
    private static final DeviceRgb TABLE_HEAD  = new DeviceRgb(30,  41,  59);
    private static final DeviceRgb SUCCESS_BG  = new DeviceRgb(220, 252, 231);
    private static final DeviceRgb SUCCESS_FG  = new DeviceRgb(22,  101, 52);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("dd MMMM yyyy")
            .withZone(ZoneId.of("Africa/Johannesburg"));

    private static final NumberFormat ZAR = NumberFormat.getInstance(new Locale("en", "ZA"));

    static {
        ZAR.setMinimumFractionDigits(2);
        ZAR.setMaximumFractionDigits(2);
    }

    public byte[] generate(HrPayslip payslip, HrEmployee employee,
                           HrPayRun payRun, String tenantName,
                           String tenantVat) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE,
                    new FooterHandler(payRun.getPayRunNumber()));

            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(50, 50, 60, 50);

            addHeader(doc, payRun, tenantName, tenantVat);
            addDivider(doc, BRAND_NAVY, 2);
            addEmployeeSection(doc, employee);
            addDivider(doc, LIGHT_GRAY, 1);
            addEarningsDeductions(doc, payslip);
            addNetPayBox(doc, payslip.getNetPay());
            addYtdSection(doc, payslip);
            addEmployerContributions(doc, payslip);
            addTaxDetail(doc, payslip);
            addLegalFooter(doc);

            doc.close();
            log.info("Generated payslip PDF for employee={} run={}",
                    employee.getEmployeeNumber(), payRun.getPayRunNumber());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Payslip PDF generation failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate payslip PDF", e);
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void addHeader(Document doc, HrPayRun payRun,
                           String tenantName, String tenantVat) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1.2f, 1}))
                .setWidth(UnitValue.createPercentValue(100));

        Cell left = new Cell().setBorder(null).setPadding(0);
        left.add(new Paragraph(tenantName)
                .setFontSize(20).setBold().setFontColor(BRAND_NAVY));
        if (tenantVat != null && !tenantVat.isBlank())
            left.add(new Paragraph("VAT No: " + tenantVat)
                    .setFontSize(9).setFontColor(MID_GRAY).setMarginTop(2));
        header.addCell(left);

        Cell right = new Cell().setBorder(null).setPadding(0)
                .setTextAlignment(TextAlignment.RIGHT);
        right.add(new Paragraph("PAYSLIP")
                .setFontSize(22).setBold().setFontColor(BRAND_TEAL));
        right.add(new Paragraph(payRun.getPayRunNumber())
                .setFontSize(11).setFontColor(MID_GRAY).setMarginTop(2));
        right.add(new Paragraph("Pay date: " + DATE_FMT.format(payRun.getPayDate().atStartOfDay(
                ZoneId.of("Africa/Johannesburg"))))
                .setFontSize(10).setFontColor(DARK_TEXT).setMarginTop(2));
        right.add(new Paragraph("Period: " + payRun.getPeriodStart() + " to " + payRun.getPeriodEnd())
                .setFontSize(9).setFontColor(MID_GRAY));
        header.addCell(right);

        doc.add(header);
    }

    // ── Employee section ──────────────────────────────────────────────────────

    private void addEmployeeSection(Document doc, HrEmployee emp) {
        doc.add(sectionTitle("Employee details"));

        Table t = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginTop(6);

        addInfoCell(t, "Full name",        emp.getFullName());
        addInfoCell(t, "Employee number",  emp.getEmployeeNumber());
        addInfoCell(t, "ID number",
                emp.getIdNumber() != null ? maskId(emp.getIdNumber()) : "—");
        addInfoCell(t, "Tax number",       emp.getTaxNumber() != null ? emp.getTaxNumber() : "—");
        addInfoCell(t, "Job title",        emp.getJobTitle()   != null ? emp.getJobTitle()  : "—");
        addInfoCell(t, "Department",       emp.getDepartment() != null ? emp.getDepartment(): "—");
        addInfoCell(t, "Employment type",  emp.getEmploymentType());
        addInfoCell(t, "Bank account",
                emp.getBankAccountNumber() != null
                        ? maskBankAccount(emp.getBankAccountNumber()) : "—");
        addInfoCell(t, "Pay frequency",    emp.getPayFrequency());
        doc.add(t);
    }

    // ── Earnings and deductions ───────────────────────────────────────────────

    private void addEarningsDeductions(Document doc, HrPayslip p) {
        Table two = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginTop(14);

        // Left: Earnings
        Cell earningsCell = new Cell().setBorder(null).setPaddingRight(8);
        earningsCell.add(sectionTitle("Earnings"));
        earningsCell.add(buildAmountTable(new String[][]{
                { "Basic salary",     fmt(p.getGrossSalary())    },
                { "Travel allowance", fmt(p.getTravelAllowance())},
                { "Overtime",         fmt(p.getOvertimeAmount()) },
                { "Bonus",            fmt(p.getBonusAmount())    },
                { "Other earnings",   fmt(p.getOtherEarnings())  },
        }, "Total earnings", fmt(p.getTotalEarnings()), BRAND_TEAL));
        two.addCell(earningsCell);

        // Right: Deductions
        Cell deductionsCell = new Cell().setBorder(null).setPaddingLeft(8);
        deductionsCell.add(sectionTitle("Deductions"));
        deductionsCell.add(buildAmountTable(new String[][]{
                { "PAYE (Income tax)", fmt(p.getPayeAmount())    },
                { "UIF (Employee)",    fmt(p.getUifEmployee())   },
                { "Medical aid",       fmt(p.getMedicalAid())    },
                { "Pension fund",      fmt(p.getPension())       },
                { "Other deductions",  fmt(p.getOtherDeductions())},
        }, "Total deductions", fmt(p.getTotalDeductions()), new DeviceRgb(220, 38, 38)));
        two.addCell(deductionsCell);

        doc.add(two);
    }

    private Table buildAmountTable(String[][] rows, String totalLabel,
                                   String totalValue, DeviceRgb totalColor) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginTop(6);

        // Header
        for (String h : new String[]{"Description", "Amount"}) {
            t.addHeaderCell(new Cell()
                    .setBackgroundColor(TABLE_HEAD).setBorder(null).setPadding(5)
                    .add(new Paragraph(h).setFontSize(9).setBold()
                            .setFontColor(ColorConstants.WHITE)));
        }

        // Rows — only show non-zero
        for (String[] row : rows) {
            BigDecimal val = parseFmt(row[1]);
            if (val.compareTo(BigDecimal.ZERO) == 0) continue;
            t.addCell(dataCell(row[0], false));
            t.addCell(amountCell(row[1], false));
        }

        // Total row
        t.addCell(new Cell()
                .setBackgroundColor(LIGHT_GRAY).setBorder(null).setPadding(6)
                .setBorderTop(new SolidBorder(totalColor, 1))
                .add(new Paragraph(totalLabel).setFontSize(10).setBold()
                        .setFontColor(DARK_TEXT)));
        t.addCell(new Cell()
                .setBackgroundColor(LIGHT_GRAY).setBorder(null).setPadding(6)
                .setBorderTop(new SolidBorder(totalColor, 1))
                .setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph("R " + totalValue).setFontSize(10).setBold()
                        .setFontColor(totalColor)));
        return t;
    }

    // ── Net pay box ───────────────────────────────────────────────────────────

    private void addNetPayBox(Document doc, BigDecimal netPay) {
        Table box = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(16).setMarginBottom(4);

        Cell label = new Cell()
                .setBackgroundColor(BRAND_TEAL).setBorder(null).setPadding(16);
        label.add(new Paragraph("NET PAY")
                .setFontSize(13).setBold().setFontColor(ColorConstants.WHITE));
        label.add(new Paragraph("Amount paid to employee")
                .setFontSize(9).setFontColor(TEAL_LIGHT));
        box.addCell(label);

        Cell amount = new Cell()
                .setBackgroundColor(BRAND_TEAL).setBorder(null).setPadding(16)
                .setTextAlignment(TextAlignment.RIGHT);
        amount.add(new Paragraph("R " + fmt(netPay))
                .setFontSize(22).setBold().setFontColor(ColorConstants.WHITE));
        box.addCell(amount);

        doc.add(box);
    }

    // ── YTD section ───────────────────────────────────────────────────────────

    private void addYtdSection(Document doc, HrPayslip p) {
        doc.add(sectionTitle("Year-to-date totals").setMarginTop(16));

        Table t = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginTop(6);

        for (String[] item : new String[][]{
                { "YTD Gross earnings", fmt(p.getYtdGross()) },
                { "YTD PAYE paid",      fmt(p.getYtdPaye())  },
                { "YTD UIF paid",       fmt(p.getYtdUif())   },
        }) {
            Cell c = new Cell()
                    .setBackgroundColor(LIGHT_GRAY).setBorder(null).setPadding(10);
            c.add(new Paragraph(item[0])
                    .setFontSize(9).setFontColor(MID_GRAY).setBold());
            c.add(new Paragraph("R " + item[1])
                    .setFontSize(13).setBold().setFontColor(BRAND_NAVY).setMarginTop(3));
            t.addCell(c);
        }
        doc.add(t);
    }

    // ── Employer contributions ────────────────────────────────────────────────

    private void addEmployerContributions(Document doc, HrPayslip p) {
        doc.add(sectionTitle("Employer contributions (not deducted from employee)")
                .setMarginTop(14));

        Table t = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginTop(6);

        for (String[] item : new String[][]{
                { "UIF (Employer)", fmt(p.getUifEmployer()) },
                { "SDL",            fmt(p.getSdlAmount())   },
                { "Total cost to company",
                        fmt(p.getTotalEarnings()
                                .add(p.getUifEmployer())
                                .add(p.getSdlAmount())) },
        }) {
            Cell c = new Cell()
                    .setBackgroundColor(new DeviceRgb(239, 246, 255))
                    .setBorder(null).setPadding(10);
            c.add(new Paragraph(item[0])
                    .setFontSize(9).setFontColor(MID_GRAY).setBold());
            c.add(new Paragraph("R " + item[1])
                    .setFontSize(12).setBold()
                    .setFontColor(new DeviceRgb(29, 78, 216)).setMarginTop(3));
            t.addCell(c);
        }
        doc.add(t);
    }

    // ── Tax calculation detail ────────────────────────────────────────────────

    private void addTaxDetail(Document doc, HrPayslip p) {
        if (p.getTaxableIncome() == null) return;

        doc.add(sectionTitle("PAYE calculation detail").setMarginTop(14));

        Table t = new Table(UnitValue.createPercentArray(new float[]{2, 1, 2, 1}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginTop(6);

        String[][] items = {
                { "Tax year",              String.valueOf(p.getTaxYear()) },
                { "Annual taxable income", "R " + fmt(p.getTaxableIncome()) },
                { "Tax before rebate",     "R " + fmt(p.getTaxBeforeRebate()) },
                { "Primary rebate",        "R " + fmt(p.getPrimaryRebate()) },
        };

        for (int i = 0; i < items.length; i++) {
            t.addCell(new Cell().setBorder(null)
                    .setBackgroundColor(i % 2 == 0 ? LIGHT_GRAY : ColorConstants.WHITE)
                    .setPadding(6)
                    .add(new Paragraph(items[i][0]).setFontSize(9).setFontColor(MID_GRAY)));
            t.addCell(new Cell().setBorder(null)
                    .setBackgroundColor(i % 2 == 0 ? LIGHT_GRAY : ColorConstants.WHITE)
                    .setPadding(6).setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph(items[i][1]).setFontSize(9).setBold()
                            .setFontColor(DARK_TEXT)));
        }
        doc.add(t);
    }

    // ── Legal footer ──────────────────────────────────────────────────────────

    private void addLegalFooter(Document doc) {
        doc.add(new Paragraph(
                "This payslip is computer generated and does not require a signature. " +
                        "Confidential — for the addressee only. " +
                        "PAYE calculated per SARS tax tables for the applicable tax year. " +
                        "UIF deducted as per the Unemployment Insurance Contributions Act.")
                .setFontSize(8).setFontColor(MID_GRAY).setItalic()
                .setMarginTop(16)
                .setBorderTop(new SolidBorder(LIGHT_GRAY, 1))
                .setPaddingTop(8));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Paragraph sectionTitle(String title) {
        return new Paragraph(title.toUpperCase())
                .setFontSize(9).setBold().setFontColor(MID_GRAY)
                .setCharacterSpacing(0.8f).setMarginBottom(0);
    }

    private void addDivider(Document doc, DeviceRgb color, float width) {
        doc.add(new Paragraph()
                .setBorderBottom(new SolidBorder(color, width))
                .setMarginTop(8).setMarginBottom(8));
    }

    private void addInfoCell(Table t, String label, String value) {
        Cell c = new Cell().setBorder(null)
                .setBackgroundColor(LIGHT_GRAY).setPadding(8);
        c.add(new Paragraph(label).setFontSize(8).setFontColor(MID_GRAY).setBold());
        c.add(new Paragraph(value != null ? value : "—")
                .setFontSize(11).setFontColor(DARK_TEXT).setMarginTop(1));
        t.addCell(c);
    }

    private Cell dataCell(String text, boolean header) {
        return new Cell().setBorder(null)
                .setBackgroundColor(header ? TABLE_HEAD : ColorConstants.WHITE)
                .setPadding(5)
                .add(new Paragraph(text).setFontSize(10)
                        .setFontColor(header ? ColorConstants.WHITE : DARK_TEXT));
    }

    private Cell amountCell(String text, boolean header) {
        return new Cell().setBorder(null)
                .setBackgroundColor(header ? TABLE_HEAD : ColorConstants.WHITE)
                .setPadding(5).setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph("R " + text).setFontSize(10)
                        .setFontColor(header ? ColorConstants.WHITE : DARK_TEXT));
    }

    private String fmt(BigDecimal val) {
        if (val == null) return "0.00";
        return ZAR.format(val);
    }

    private BigDecimal parseFmt(String s) {
        if (s == null) return BigDecimal.ZERO;
        try {
            // ZAR format uses space as thousands separator and comma as decimal
            // "27 000,00" → "27000.00"
            String clean = s
                    .replace("\u00a0", "")  // non-breaking space
                    .replace(" ", "")       // regular space (thousands separator)
                    .replace(",", ".");     // comma decimal → dot decimal
            return new BigDecimal(clean);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String maskId(String id) {
        if (id == null || id.length() < 6) return id;
        return id.substring(0, 6) + "*****";
    }

    private String maskBankAccount(String acc) {
        if (acc == null || acc.length() < 4) return "****";
        return "****" + acc.substring(acc.length() - 4);
    }

    // ── Page footer ───────────────────────────────────────────────────────────

    private static class FooterHandler implements IEventHandler {
        private final String payRunNumber;
        FooterHandler(String payRunNumber) { this.payRunNumber = payRunNumber; }

        @Override
        public void handleEvent(Event event) {
            try {
                PdfDocumentEvent e = (PdfDocumentEvent) event;
                PdfDocument pdfDoc = e.getDocument();
                PdfPage page       = e.getPage();
                int pageNum        = pdfDoc.getPageNumber(page);
                int total          = pdfDoc.getNumberOfPages();

                PdfCanvas canvas = new PdfCanvas(page);
                canvas.beginText()
                        .setFontAndSize(pdfDoc.getDefaultFont(), 8)
                        .moveText(50, 28)
                        .showText("HandyFlow · " + payRunNumber +
                                " · Confidential · Page " + pageNum + " of " + total)
                        .endText();
                canvas.release();
            } catch (Exception ignored) {}
        }
    }
}