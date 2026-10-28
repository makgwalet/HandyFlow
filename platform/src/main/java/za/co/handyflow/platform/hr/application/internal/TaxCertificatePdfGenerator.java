package za.co.handyflow.platform.hr.application.internal;

import com.itextpdf.kernel.colors.ColorConstants;
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
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.dto.TaxCertificateDtos.Certificate;
import za.co.handyflow.platform.hr.dto.TaxCertificateDtos.Line;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * An employee's tax certificate (IRP5 or IT3(a)) as a PDF: who it is for, the period, and the SARS code and amount of each line. It carries the
 * figures that were sent to SARS, laid out for the employee to read. It is not SARS's own printed form.
 */
@Component
public class TaxCertificatePdfGenerator {

    private static final DeviceRgb NAVY = new DeviceRgb(27, 58, 107);
    private static final DeviceRgb GRAY = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb ROW_ALT = new DeviceRgb(241, 245, 249);
    private static final DeviceRgb WARN_BG = new DeviceRgb(254, 226, 226);
    private static final DeviceRgb WARN_FG = new DeviceRgb(153, 27, 27);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static NumberFormat money() {
        NumberFormat f = NumberFormat.getInstance(new Locale("en", "ZA"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f;
    }

    /** Which block of the certificate a code belongs to. */
    static String section(String code) {
        if (code.startsWith("36") || code.startsWith("37") || code.startsWith("38") || code.startsWith("39")) return "Income";
        if (code.equals("4102") || code.equals("4101") || code.equals("4115") || code.equals("4116") || code.equals("4141") || code.equals("4142") || code.equals("4149"))
            return "Tax, UIF and SDL";
        return "Deductions and contributions";
    }

    public byte[] generate(Certificate c, String employerName, String employerPayeReference) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Document doc = new Document(new PdfDocument(new PdfWriter(bos)), PageSize.A4);
            doc.setMargins(36, 36, 36, 36);
            NumberFormat zar = money();

            Table head = new Table(UnitValue.createPercentArray(new float[]{65, 35})).setWidth(UnitValue.createPercentValue(100));
            head.addCell(new Cell().setBorder(null).setBackgroundColor(NAVY).setPadding(14)
                    .add(new Paragraph("IT3A".equals(c.certificateType()) ? "IT3(a)" : c.certificateType() == null ? "Tax certificate" : c.certificateType()).setFontSize(26).setBold().setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph("Employees' tax certificate").setFontSize(10).setFontColor(ColorConstants.WHITE)));
            head.addCell(new Cell().setBorder(null).setBackgroundColor(NAVY).setPadding(14).setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph("Year of assessment").setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph(String.valueOf(c.taxYear())).setFontSize(18).setBold().setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph("INTERIM".equals(c.period()) ? "Interim (to 31 August)" : "Annual").setFontSize(9).setFontColor(ColorConstants.WHITE)));
            doc.add(head);

            if ("CANCELLED".equals(c.status()))
                doc.add(new Paragraph("CANCELLED" + (c.cancelReason() == null ? "" : ": " + c.cancelReason()) + ". Do not use this certificate.")
                        .setFontSize(10).setBold().setFontColor(WARN_FG).setBackgroundColor(WARN_BG).setPadding(6).setMarginTop(8).setTextAlignment(TextAlignment.CENTER));

            Table who = new Table(UnitValue.createPercentArray(new float[]{50, 50})).setWidth(UnitValue.createPercentValue(100)).setMarginTop(14);
            who.addCell(block("Employer", List.of(employerName == null ? "" : employerName,
                    "PAYE reference: " + (employerPayeReference == null ? "not set" : employerPayeReference))));
            who.addCell(block("Employee", List.of(c.employeeName() == null ? "" : c.employeeName(),
                    identification(c),
                    "Income tax number: " + (c.taxNumber() == null ? "not recorded" : c.taxNumber()),
                    "Employee number: " + (c.employeeNumber() == null ? "" : c.employeeNumber()))));
            doc.add(who);

            Table meta = new Table(UnitValue.createPercentArray(new float[]{34, 33, 33})).setWidth(UnitValue.createPercentValue(100)).setMarginTop(8);
            meta.addCell(block("Certificate number", List.of(c.certificateNumber() == null ? "" : c.certificateNumber())));
            meta.addCell(block("Period", List.of(c.periodFrom() == null ? "" : DATE.format(c.periodFrom()) + " to " + (c.periodTo() == null ? "" : DATE.format(c.periodTo())))));
            meta.addCell(block("Issued", List.of(c.issuedAt() == null ? "" : DATE.format(c.issuedAt().atZone(java.time.ZoneId.of("Africa/Johannesburg"))) + (c.version() > 1 ? " (version " + c.version() + ")" : ""))));
            doc.add(meta);

            for (String section : List.of("Income", "Deductions and contributions", "Tax, UIF and SDL")) {
                List<Line> lines = c.lines().stream().filter(l -> section.equals(section(l.code()))).toList();
                if (lines.isEmpty()) continue;
                doc.add(new Paragraph(section).setBold().setFontSize(11).setFontColor(NAVY).setMarginTop(14).setMarginBottom(4));
                Table t = new Table(UnitValue.createPercentArray(new float[]{12, 68, 20})).setWidth(UnitValue.createPercentValue(100));
                t.addHeaderCell(headCell("Code", false));
                t.addHeaderCell(headCell("Description", false));
                t.addHeaderCell(headCell("Amount (R)", true));
                int i = 0;
                for (Line l : lines) {
                    DeviceRgb bg = i++ % 2 == 1 ? ROW_ALT : null;
                    t.addCell(bodyCell(l.code(), false, bg));
                    t.addCell(bodyCell(l.description(), false, bg));
                    t.addCell(bodyCell(zar.format(l.amount() == null ? BigDecimal.ZERO : l.amount()), true, bg));
                }
                doc.add(t);
            }

            doc.add(new Paragraph("These are the figures reported to SARS for this period. Keep this certificate with your tax records; you need it when you "
                    + "submit your income tax return. It is produced from your employer's payroll and is not SARS's printed form.")
                    .setFontSize(8).setFontColor(GRAY).setMarginTop(18));
            doc.close();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("The certificate PDF could not be made: " + e.getMessage(), e);
        }
    }

    private static Cell block(String title, List<String> rows) {
        Cell cell = new Cell().setBorder(new SolidBorder(new DeviceRgb(226, 232, 240), 0.5f)).setPadding(8);
        cell.add(new Paragraph(title).setFontSize(8).setBold().setFontColor(GRAY).setMarginBottom(2));
        for (String r : rows) cell.add(new Paragraph(r).setFontSize(10).setMargin(0));
        return cell;
    }

    private static Cell headCell(String text, boolean right) {
        Cell c = new Cell().setBackgroundColor(NAVY).setBorder(null).setPadding(5)
                .add(new Paragraph(text).setFontSize(9).setBold().setFontColor(ColorConstants.WHITE));
        return right ? c.setTextAlignment(TextAlignment.RIGHT) : c;
    }

    private static Cell bodyCell(String text, boolean right, DeviceRgb bg) {
        Cell c = new Cell().setBorder(null).setPadding(4).add(new Paragraph(text == null ? "" : text).setFontSize(9.5f).setMargin(0));
        if (bg != null) c.setBackgroundColor(bg);
        return right ? c.setTextAlignment(TextAlignment.RIGHT) : c;
    }

    /** "ID number: ..." when there is one, otherwise the passport and its country. */
    static String identification(Certificate c) {
        boolean id = c.idNumber() != null && !c.idNumber().isBlank();
        boolean passport = c.passportNumber() != null && !c.passportNumber().isBlank();
        if (id || !passport) return "ID number: " + (id ? c.idNumber() : "not recorded");
        return "Passport: " + c.passportNumber() + (c.passportCountry() == null || c.passportCountry().isBlank() ? "" : " (" + c.passportCountry() + ")");
    }
}
