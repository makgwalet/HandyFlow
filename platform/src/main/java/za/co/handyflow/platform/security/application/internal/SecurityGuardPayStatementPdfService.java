// security/application/internal/SecurityGuardPayStatementPdfService.java

package za.co.handyflow.platform.security.application.internal;

import com.itextpdf.kernel.colors.Color;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.PayrollLineItem;
import za.co.handyflow.platform.security.domain.model.PayrollPeriod;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.PayrollLineItemRepository;
import za.co.handyflow.platform.security.domain.repository.PayrollPeriodRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * SecurityGuardPayStatementPdfService — a gross-pay statement for one guard
 * over one payroll period. DELIBERATELY NOT CALLED "Payslip" or built as
 * one, and does NOT reuse HR's PayslipPdfGenerator, despite the original
 * audit suggesting exactly that reuse. Reasoning:
 *
 * PayslipPdfGenerator (za.co.handyflow.platform.hr) expects HrPayslip/
 * HrEmployee/HrPayRun -- a full statutory payroll model with PAYE, UIF
 * (employee + employer), medical aid, pension, SDL, and a tax-calculation
 * breakdown. This module's payroll domain (PayrollPeriod/PayrollLineItem)
 * computes ONLY gross pay -- regular + overtime hours x grade rate -- and
 * explicitly hands off to external payroll software from there (see
 * PayrollController's own javadoc: "download for Sage/VIP Payroll"). There
 * is no PAYE/UIF/tax computation anywhere in this module.
 *
 * Force-mapping this data into HR's generator would mean either fabricating
 * statutory-deduction values that don't exist here, or rendering a document
 * titled "PAYSLIP" with a PAYE/UIF section showing R0.00 -- which reads as
 * "no tax was withheld" (a compliance claim this system has no basis to
 * make) rather than "this system doesn't compute tax" (the truth; that
 * happens downstream in Sage/VIP after CSV import). That's not a safe
 * shortcut, so this is a separate, honestly-scoped document instead: gross
 * hours/rate/pay only, with an explicit statement that statutory deductions
 * are computed externally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityGuardPayStatementPdfService {

    private static final DeviceRgb BRAND_TEAL  = new DeviceRgb(13, 148, 136); // matches SecurityPayrollService's own domain color family
    private static final DeviceRgb LIGHT_GREY  = new DeviceRgb(247, 247, 247);
    private static final DeviceRgb MID_GREY    = new DeviceRgb(200, 200, 200);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneOffset.UTC);

    private final SecurityPdfBrandingHelper brandingHelper;
    private final PayrollPeriodRepository   periodRepository;
    private final PayrollLineItemRepository lineItemRepository;
    private final GuardRepository           guardRepository;

    public byte[] payStatementPdf(TenantId tenantId, UUID periodId, UUID guardId) {
        PayrollPeriod period = periodRepository.findByTenantAndId(tenantId, periodId)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollPeriod", periodId.toString()));
        Guard guard = guardRepository.findActiveById(tenantId, guardId)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", guardId.toString()));
        List<PayrollLineItem> items = lineItemRepository.findByPeriodAndGuard(periodId, guardId);

        TenantDetails tenant = brandingHelper.resolveTenant(tenantId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc  = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            brandingHelper.addBrandedHeader(doc, "Guard Pay Statement",
                    guard.getFullName(), period.getName(), tenant, BRAND_TEAL);

            addGuardDetails(doc, guard, period);
            addLineItemsTable(doc, items);
            addTotalsBox(doc, items);
            addStatutoryDisclaimer(doc);

            brandingHelper.addBrandedFooter(doc, tenant);
        } catch (Exception e) {
            log.error("[Payroll] Pay statement PDF generation failed guardId={} periodId={}: {}",
                    guardId, periodId, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    private void addGuardDetails(Document doc, Guard guard, PayrollPeriod period) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(16);

        addRow(table, "Guard", guard.getFullName());
        addRow(table, "Grade", guard.getGrade() != null ? guard.getGrade() : "—");
        addRow(table, "PSiRA Number", guard.getPsiraNumber() != null ? guard.getPsiraNumber() : "—");
        addRow(table, "Pay Period", period.getPeriodStart() + " to " + period.getPeriodEnd());

        doc.add(table);
    }

    private void addLineItemsTable(Document doc, List<PayrollLineItem> items) {
        doc.add(new Paragraph("Hours & Pay")
                .setFontSize(11).setBold()
                .setFontColor(BRAND_TEAL)
                .setMarginTop(8).setMarginBottom(4));

        if (items.isEmpty()) {
            doc.add(new Paragraph("No payable shifts recorded for this guard in this period.")
                    .setFontSize(10).setMarginBottom(16));
            return;
        }

        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 2, 2, 2, 2}))
                .useAllAvailableWidth()
                .setMarginBottom(12);

        for (String h : new String[]{"Shift Date", "Type", "Hours", "Rate/hr", "Gross"}) {
            table.addCell(new Cell()
                    .add(new Paragraph(h).setFontSize(8).setBold().setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(BRAND_TEAL)
                    .setBorder(new SolidBorder(BRAND_TEAL, 0.5f))
                    .setPadding(5));
        }

        boolean alt = false;
        for (PayrollLineItem li : items) {
            Color bg = alt ? LIGHT_GREY : ColorConstants.WHITE;
            BigDecimal hours = li.getLineType().name().equals("OVERTIME")
                    ? li.getOvertimeHours() : li.getHoursWorked();
            int rateCents = li.getLineType().name().equals("OVERTIME")
                    ? li.getOvertimeRateCents() : li.getHourlyRateCents();

            table.addCell(cell(DATE_FMT.format(li.getShiftStartAt()), bg));
            table.addCell(cell(li.getLineType().name(), bg));
            table.addCell(cell(hours.toPlainString(), bg));
            table.addCell(cell(fmtCents(rateCents), bg));
            table.addCell(cell(fmtCents(li.getGrossAmountCents()), bg));
            alt = !alt;
        }
        doc.add(table);
    }

    private void addTotalsBox(Document doc, List<PayrollLineItem> items) {
        BigDecimal totalHours = items.stream()
                .map(li -> li.getHoursWorked().add(li.getOvertimeHours()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalCents = items.stream().mapToLong(PayrollLineItem::getGrossAmountCents).sum();

        Table box = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(16);

        Cell label = new Cell().setBackgroundColor(BRAND_TEAL).setPadding(12)
                .add(new Paragraph("TOTAL GROSS PAY").setFontSize(11).setBold()
                        .setFontColor(ColorConstants.WHITE))
                .add(new Paragraph(totalHours.toPlainString() + " hours").setFontSize(9)
                        .setFontColor(ColorConstants.WHITE));
        Cell amount = new Cell().setBackgroundColor(BRAND_TEAL).setPadding(12)
                .setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph(fmtCents(totalCents)).setFontSize(18).setBold()
                        .setFontColor(ColorConstants.WHITE));

        box.addCell(label);
        box.addCell(amount);
        doc.add(box);
    }

    /**
     * The load-bearing paragraph on this whole document — see class javadoc.
     * Without this, a guard or manager could reasonably mistake "gross pay"
     * for "take-home pay."
     */
    private void addStatutoryDisclaimer(Document doc) {
        doc.add(new Paragraph(
                "This is a gross pay statement, not a statutory payslip. PAYE, UIF, and any "
                        + "other statutory deductions are computed by your external payroll system "
                        + "(e.g. Sage, VIP Payroll) after this data is exported — figures above do not "
                        + "reflect take-home pay.")
                .setFontSize(8).setItalic()
                .setFontColor(new DeviceRgb(120, 120, 120))
                .setMarginTop(8)
                .setBorderTop(new SolidBorder(MID_GREY, 1))
                .setPaddingTop(8));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String fmtCents(long cents) {
        return "R " + String.format(java.util.Locale.US, "%,.2f", cents / 100.0);
    }

    private Cell cell(String text, Color bg) {
        return new Cell()
                .add(new Paragraph(text).setFontSize(9))
                .setBackgroundColor(bg)
                .setBorder(new SolidBorder(MID_GREY, 0.5f))
                .setPadding(5);
    }

    private void addRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setFontSize(10))
                .setBackgroundColor(LIGHT_GREY)
                .setBorder(new SolidBorder(MID_GREY, 0.5f))
                .setPadding(6));
        table.addCell(new Cell().add(new Paragraph(value).setFontSize(10).setBold())
                .setBackgroundColor(ColorConstants.WHITE)
                .setBorder(new SolidBorder(MID_GREY, 0.5f))
                .setPadding(6)
                .setTextAlignment(TextAlignment.RIGHT));
    }
}