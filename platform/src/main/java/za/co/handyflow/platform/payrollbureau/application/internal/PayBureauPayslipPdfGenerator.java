package za.co.handyflow.platform.payrollbureau.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.payrollbureau.domain.model.PayClient;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee;
import za.co.handyflow.platform.payrollbureau.domain.model.PayRun;
import za.co.handyflow.platform.payrollbureau.domain.model.Payslip;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import com.lowagie.text.Image;

/**
 * Payroll Bureau's own payslip PDF — deliberately NOT a reuse of
 * hr.PayslipPdfGenerator, mirroring the same reasoning
 * SecurityGuardPayStatementPdfService already established for not
 * force-reusing across an isolated module boundary (hr may not be
 * depended on — see PayrollBureauEngine's own class Javadoc for the
 * full rationale, now backed by a real passing parity test).
 * <p>
 * Built on OpenPDF (com.lowagie.text), matching the majority precedent
 * in this codebase (RecruiterPdfGenerator, AccFeeNotePdfGenerator,
 * CreativePdfGenerator) — deliberately NOT itext7-core, which carries a
 * real AGPL-unless-commercially-licensed flag already documented
 * elsewhere in this codebase (BookingConfirmationPdfService's own
 * Javadoc).
 * <p>
 * UPDATED: logo + client address support — the third real EvidenceFacade
 * consumer. logoBytes is genuinely optional: null (no logo attached) or
 * a corrupt/unsupported image both degrade gracefully to the original
 * text-only header, never throw and block the whole payslip.
 */
@Component
public class PayBureauPayslipPdfGenerator {

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
    private static final Font VALUE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font TOTAL_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] generate(Payslip payslip, PayEmployee employee, PayRun payRun, PayClient client, byte[] logoBytes) {
        Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // Logo — genuinely optional. A bad/corrupt image degrades to
            // the text-only header below rather than throwing and
            // blocking the whole payslip from generating.
            if (logoBytes != null && logoBytes.length > 0) {
                try {
                    Image logo = Image.getInstance(logoBytes);
                    logo.scaleToFit(140, 60);
                    logo.setAlignment(Element.ALIGN_LEFT);
                    doc.add(logo);
                } catch (Exception e) {
                    // Not a valid/supported image format — fall through
                    // to the text-only title below, same as if no logo
                    // had ever been attached.
                }
            }

            doc.add(new Paragraph(client.getTradingName(), TITLE_FONT));
            if (client.getAddress() != null && !client.getAddress().isBlank()) {
                doc.add(new Paragraph(client.getAddress(), LABEL_FONT));
            }
            doc.add(new Paragraph("PAYSLIP", HEADER_FONT));
            doc.add(new Paragraph(" "));

            // Reference details
            PdfPTable refTable = new PdfPTable(2);
            refTable.setWidthPercentage(100);
            addRow(refTable, "Employee", employee.getFirstName() + " " + employee.getLastName());
            addRow(refTable, "Employee Number", employee.getEmployeeNumber());
            addRow(refTable, "Pay Period", payRun.getPeriodStart().format(DATE_FMT) + " – " + payRun.getPeriodEnd().format(DATE_FMT));
            addRow(refTable, "Pay Date", payRun.getPayDate().format(DATE_FMT));
            addRow(refTable, "Tax Year", String.valueOf(payslip.getTaxYear()));
            doc.add(refTable);
            doc.add(new Paragraph(" "));

            // Earnings
            doc.add(new Paragraph("Earnings", HEADER_FONT));
            PdfPTable earnings = new PdfPTable(2);
            earnings.setWidthPercentage(100);
            addAmountRow(earnings, "Gross Salary", payslip.getGrossSalary());
            addAmountRow(earnings, "Travel Allowance", payslip.getTravelAllowance());
            addAmountRow(earnings, "Total Earnings", payslip.getTotalEarnings());
            doc.add(earnings);
            doc.add(new Paragraph(" "));

            // Deductions
            doc.add(new Paragraph("Deductions", HEADER_FONT));
            PdfPTable deductions = new PdfPTable(2);
            deductions.setWidthPercentage(100);
            addAmountRow(deductions, "PAYE", payslip.getPayeAmount());
            addAmountRow(deductions, "UIF (Employee)", payslip.getUifEmployee());
            addAmountRow(deductions, "Medical Aid", payslip.getMedicalAid());
            addAmountRow(deductions, "Pension", payslip.getPension());
            addAmountRow(deductions, "Total Deductions", payslip.getTotalDeductions());
            doc.add(deductions);
            doc.add(new Paragraph(" "));

            // Net pay — the one figure that matters most, largest and bold
            PdfPTable netTable = new PdfPTable(2);
            netTable.setWidthPercentage(100);
            PdfPCell netLabel = new PdfPCell(new Phrase("NET PAY", TOTAL_FONT));
            netLabel.setBorder(Rectangle.TOP);
            netLabel.setPaddingTop(8);
            PdfPCell netValue = new PdfPCell(new Phrase(formatCurrency(payslip.getNetPay()), TOTAL_FONT));
            netValue.setBorder(Rectangle.TOP);
            netValue.setPaddingTop(8);
            netValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            netTable.addCell(netLabel);
            netTable.addCell(netValue);
            doc.add(netTable);
            doc.add(new Paragraph(" "));

            // Employer contributions — informational, not part of the employee's own deductions
            doc.add(new Paragraph("Employer Contributions (informational — not deducted from your pay)", LABEL_FONT));
            PdfPTable employerTable = new PdfPTable(2);
            employerTable.setWidthPercentage(100);
            addAmountRow(employerTable, "UIF (Employer)", payslip.getUifEmployer());
            addAmountRow(employerTable, "SDL", payslip.getSdlAmount());
            doc.add(employerTable);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(
                    "Payroll processed by " + client.getTradingName() + "'s payroll bureau via HandyFlow. "
                            + "PAYE Ref: " + (client.getPayeReference() != null ? client.getPayeReference() : "—")
                            + " · UIF Ref: " + (client.getUifReference() != null ? client.getUifReference() : "—"),
                    LABEL_FONT));

            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to generate payslip PDF for payslip=" + payslip.getId(), e);
        }
    }

    private void addRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "—", VALUE_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addAmountRow(PdfPTable table, String label, BigDecimal amount) {
        addRow(table, label, formatCurrency(amount));
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        return "R " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}