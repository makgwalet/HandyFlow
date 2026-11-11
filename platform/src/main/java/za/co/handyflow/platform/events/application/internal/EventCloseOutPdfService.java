package za.co.handyflow.platform.events.application.internal;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.dto.BudgetSummaryResponse;
import za.co.handyflow.platform.events.dto.CloseOutResponse;
import za.co.handyflow.platform.events.dto.RegistrationDashboardResponse;
import za.co.handyflow.platform.events.dto.SupplierSummary;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The downloadable close-out pack: one PDF that answers "how did the event go,
 * and is it finished?" — attendance reconciliation, registrations by tier,
 * budget vs actual, supplier balances, and the close-out checklist.
 * <p>
 * Deliberately contains NO guest names, emails or other personal information —
 * only counts and money — so it can be shared with finance, a client or a
 * board without the POPIA considerations the guest register carries. It is
 * rendered from the same CloseOutResponse and RegistrationDashboardResponse
 * the dashboards serve, so the PDF and the screens can never disagree.
 * <p>
 * An event that isn't COMPLETED yet still produces a pack, clearly stamped
 * as an interim position rather than a final one.
 */
@Slf4j
@Service
class EventCloseOutPdfService {

    private static final DeviceRgb NAVY    = new DeviceRgb(27, 58, 107);
    private static final DeviceRgb GREY    = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb LIGHT   = new DeviceRgb(241, 245, 249);
    private static final DeviceRgb GREEN   = new DeviceRgb(22, 101, 52);
    private static final DeviceRgb RED     = new DeviceRgb(185, 28, 28);
    private static final DeviceRgb AMBER   = new DeviceRgb(180, 83, 9);
    private static final DeviceRgb RULE    = new DeviceRgb(226, 232, 240);

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH);

    byte[] generate(Event event, RegistrationDashboardResponse registration, CloseOutResponse closeOut) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 36, 36, 36);

            addHeader(doc, event, closeOut);
            addAttendance(doc, closeOut.attendance());
            addTiers(doc, registration);
            addBudget(doc, closeOut.budget());
            addSuppliers(doc, closeOut);
            addChecklist(doc, closeOut);
            addFooter(doc);

            doc.close();
        } catch (Exception e) {
            log.error("[Events] Close-out PDF generation failed event={}: {}", event.getEventNumber(), e.getMessage(), e);
            throw new RuntimeException("Close-out pack generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private void addHeader(Document doc, Event event, CloseOutResponse closeOut) {
        doc.add(new Paragraph("HandyFlow Events — Close-out pack")
                .setFontSize(9).setBold().setFontColor(GREY));
        doc.add(new Paragraph(event.getTitle())
                .setFontSize(20).setBold().setFontColor(NAVY).setMarginTop(2).setMarginBottom(2));

        StringBuilder meta = new StringBuilder(event.getEventNumber());
        if (event.getStartDatetime() != null) {
            meta.append("  |  ").append(event.getStartDatetime().format(DATE_TIME));
        }
        if (event.getVenueName() != null && !event.getVenueName().isBlank()) {
            meta.append("  |  ").append(event.getVenueName());
        }
        doc.add(new Paragraph(meta.toString()).setFontSize(10).setFontColor(GREY).setMarginBottom(6));

        boolean completed = "COMPLETED".equals(closeOut.status());
        Paragraph banner = new Paragraph(completed
                ? "Final position — event COMPLETED"
                : "INTERIM position — event is " + closeOut.status() + ", figures are not final")
                .setFontSize(10).setBold().setFontColor(completed ? GREEN : AMBER)
                .setBackgroundColor(LIGHT).setPadding(6).setMarginBottom(10);
        doc.add(banner);
    }

    private void addAttendance(Document doc, CloseOutResponse.AttendanceSummary a) {
        section(doc, "1. Attendance reconciliation");
        Table t = twoColumn();
        row(t, "Registered (live)", String.valueOf(a.registered()));
        row(t, "Checked in", String.valueOf(a.checkedIn()));
        row(t, "Registered but not checked in", String.valueOf(a.notCheckedIn()));
        row(t, "Walk-ins (registered at the door)", String.valueOf(a.walkIns()));
        row(t, "Cancelled registrations", String.valueOf(a.cancelled()));
        row(t, "Tickets transferred to another person", String.valueOf(a.transferred()));
        row(t, "Attendance rate",
                a.attendanceRatePercent() == null ? "n/a" : a.attendanceRatePercent().toPlainString() + "%");
        doc.add(t);
    }

    private void addTiers(Document doc, RegistrationDashboardResponse reg) {
        section(doc, "2. Registrations by ticket tier");
        if (reg.tiers().isEmpty()) {
            note(doc, "No ticket tiers were set up for this event.");
            return;
        }
        Table t = table(new float[]{34, 14, 12, 12, 14, 14});
        header(t, "Tier", "Price", "Sold", "Of", "Checked in", "List value");
        for (RegistrationDashboardResponse.TierRow r : reg.tiers()) {
            cell(t, r.name(), false);
            cell(t, money(r.price()), true);
            cell(t, String.valueOf(r.sold()), true);
            cell(t, String.valueOf(r.quantity()), true);
            cell(t, String.valueOf(r.checkedIn()), true);
            cell(t, money(r.faceValueSold()), true);
        }
        doc.add(t);
        note(doc, "List value is price x tickets sold. Amounts staff recorded as received: "
                + money(reg.amountRecorded()) + " (not reconciled to a payment provider).");
    }

    private void addBudget(Document doc, BudgetSummaryResponse b) {
        section(doc, "3. Budget vs actual");
        if ("NOT_STARTED".equals(b.budgetStatus()) && b.lines().isEmpty()) {
            note(doc, "No budget was recorded for this event.");
            return;
        }
        note(doc, "Budget status: " + b.budgetStatus());

        Table t = table(new float[]{26, 14, 14, 14, 14, 18});
        header(t, "Line", "Planned", "Committed", "Invoiced", "Paid", "Variance");
        for (BudgetSummaryResponse.LineSummary l : b.lines()) {
            cell(t, l.description() + " (" + l.category() + ")", false);
            cell(t, money(l.planned()), true);
            cell(t, money(l.committed()), true);
            cell(t, money(l.invoiced()), true);
            cell(t, money(l.paid()), true);
            cell(t, money(l.variance()), true, l.overBudget() ? RED : null);
        }
        boldCell(t, "TOTAL", false);
        boldCell(t, money(b.planned()), true);
        boldCell(t, money(b.committed()), true);
        boldCell(t, money(b.invoiced()), true);
        boldCell(t, money(b.paid()), true);
        boldCell(t, money(b.variance()), true);
        doc.add(t);

        Table s = twoColumn();
        row(s, "Forecast final cost", money(b.forecast()));
        row(s, "Still to pay", money(b.outstanding()));
        row(s, "Cost per registered guest", b.costPerRegistered() == null ? "n/a" : money(b.costPerRegistered()));
        row(s, "Cost per guest who attended", b.costPerCheckedIn() == null ? "n/a" : money(b.costPerCheckedIn()));
        s.setMarginTop(8);
        doc.add(s);
        note(doc, "Variance = planned minus forecast; negative means over budget. Amounts are as recorded "
                + "(VAT inclusive or exclusive, consistently) and are tracked here, not in the accounting ledger.");
    }

    private void addSuppliers(Document doc, CloseOutResponse closeOut) {
        section(doc, "4. Supplier balances");
        if (closeOut.suppliers().isEmpty()) {
            note(doc, "No approved supplier commitments.");
            return;
        }
        Table t = table(new float[]{34, 8, 15, 15, 14, 14});
        header(t, "Supplier", "No.", "Committed", "Invoiced", "Paid", "Outstanding");
        for (SupplierSummary s : closeOut.suppliers()) {
            cell(t, s.vendorName(), false);
            cell(t, String.valueOf(s.commitmentCount()), true);
            cell(t, money(s.committed()), true);
            cell(t, money(s.invoiced()), true);
            cell(t, money(s.paid()), true);
            cell(t, money(s.outstanding()), true, s.outstanding().signum() > 0 ? AMBER : null);
        }
        doc.add(t);
    }

    private void addChecklist(Document doc, CloseOutResponse closeOut) {
        section(doc, "5. Close-out checklist");
        Table t = table(new float[]{12, 44, 44});
        header(t, "Result", "Check", "Detail");
        for (CloseOutResponse.CloseOutCheck c : closeOut.checks()) {
            cell(t, c.passed() ? "PASS" : "OPEN", false, c.passed() ? GREEN : RED);
            cell(t, c.label(), false);
            cell(t, c.detail(), false);
        }
        doc.add(t);
        doc.add(new Paragraph(closeOut.readyToClose()
                ? "All checks passed — this event is ready to close."
                : "Some checks are still open — see the items marked OPEN.")
                .setFontSize(10).setBold()
                .setFontColor(closeOut.readyToClose() ? GREEN : AMBER).setMarginTop(8));
    }

    private void addFooter(Document doc) {
        doc.add(new Paragraph("Generated " + ZonedDateTime.now(SAST).format(DATE_TIME)
                + " from live HandyFlow data. Contains no personal information about guests.")
                .setFontSize(8).setFontColor(GREY).setTextAlignment(TextAlignment.CENTER).setMarginTop(16));
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private void section(Document doc, String title) {
        doc.add(new Paragraph(title).setFontSize(12).setBold().setFontColor(NAVY)
                .setMarginTop(14).setMarginBottom(4));
    }

    private void note(Document doc, String text) {
        doc.add(new Paragraph(text).setFontSize(8).setFontColor(GREY).setMarginTop(2).setMarginBottom(2));
    }

    private Table table(float[] widths) {
        return new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
    }

    private Table twoColumn() {
        return table(new float[]{60, 40});
    }

    private void row(Table t, String label, String value) {
        t.addCell(plain(label, false, null, false));
        t.addCell(plain(value, true, null, true));
    }

    private void header(Table t, String... labels) {
        for (int i = 0; i < labels.length; i++) {
            Cell c = new Cell().setBackgroundColor(NAVY).setBorder(Border.NO_BORDER).setPadding(4);
            c.add(new Paragraph(labels[i]).setFontSize(8).setBold().setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(i == 0 ? TextAlignment.LEFT : TextAlignment.RIGHT));
            t.addCell(c);
        }
    }

    private void cell(Table t, String text, boolean right) {
        t.addCell(plain(text, right, null, false));
    }

    private void cell(Table t, String text, boolean right, DeviceRgb color) {
        t.addCell(plain(text, right, color, false));
    }

    private void boldCell(Table t, String text, boolean right) {
        t.addCell(plain(text, right, null, true));
    }

    private Cell plain(String text, boolean right, DeviceRgb color, boolean bold) {
        Paragraph p = new Paragraph(text == null ? "" : text).setFontSize(9)
                .setTextAlignment(right ? TextAlignment.RIGHT : TextAlignment.LEFT);
        if (bold) p.setBold();
        if (color != null) p.setFontColor(color);
        return new Cell().setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(RULE, 0.5f)).setPadding(4).add(p);
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "R0.00" : String.format(Locale.ENGLISH, "R%,.2f", amount);
    }
}