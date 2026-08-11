package za.co.handyflow.platform.crm.application.internal;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.CustomerActivity;
import za.co.handyflow.platform.crm.domain.repository.CustomerActivityRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PopiaExportPdfService — human-readable version of the POPIA data subject
 * export.
 *
 * FIX: "no POPIA export as PDF" gap — PopiaExportService's own doc comment
 * flagged this as a known future step ("PDF generation is a future step,
 * using the JSON as input"). Not every recipient of a data-subject export
 * wants raw JSON; some POPIA requests are more naturally fulfilled with a
 * document a non-technical person can actually read.
 * <p>
 * WHY built directly from Customer + CustomerActivity, deliberately NOT
 * sharing PopiaExportService.buildExport()/PopiaExportDto? This session
 * doesn't have PopiaExportDto.java's actual record declaration — only the
 * positional constructor call inside PopiaExportService, which confirms
 * field ORDER but not the record's real component NAMES (a record's
 * accessors are named after its declared components, not guessable from a
 * constructor call alone). An earlier draft of this exact file called
 * export.personalData() and export.activities() on that assumption —
 * caught and discarded before shipping, since a wrong guess there is a
 * compile error in a file this session can't see to fix. This version
 * queries the same two repositories PopiaExportService itself queries —
 * Customer and CustomerActivity — whose real accessor methods are already
 * confirmed working via CustomerProfilePdfService's existing, tested
 * usage of them. Same underlying data, zero risk of a guessed accessor
 * name breaking the build. The tradeoff: this duplicates PopiaExportService's
 * data-gathering query rather than sharing it — a reasonable price for not
 * guessing at an unseen file's API.
 * <p>
 * Same visual family as CustomerProfilePdfService (navy header bar, same
 * brand colours, same section-heading/row conventions) so this reads as
 * part of the same document set.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PopiaExportPdfService {

    private static final ZoneId            SAST     = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy").withZone(SAST);
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(SAST);

    private static final DeviceRgb NAVY  = new DeviceRgb(27, 58, 107);
    private static final DeviceRgb LIGHT = new DeviceRgb(239, 246, 255);
    private static final DeviceRgb MUTED = new DeviceRgb(100, 116, 139);

    private final CustomerRepository         customerRepository;
    private final CustomerActivityRepository activityRepository;
    private final JdbcTemplate               jdbc;

    @Transactional
    public void generatePdf(TenantId tenantId, UUID customerId, UUID requestedBy, OutputStream out)
            throws IOException {

        var customer = customerRepository.findActiveById(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId.toString()));

        // Full, unbounded, chronological (oldest-first) history — same
        // "POPIA requires complete history, in story order" reasoning
        // CustomerActivityRepository.findAllByCustomer's own doc comment
        // gives, and the exact query PopiaExportService itself uses.
        var activities = activityRepository.findAllByCustomer(tenantId, customerId);

        writeDocument(customer, activities, requestedBy, out);

        // Same audit-trail guarantee as the JSON export — every export,
        // regardless of format, must itself be a provable, queryable event.
        customer.addNote(
                "POPIA data export (PDF) generated by user " + requestedBy + " at " + Instant.now(),
                requestedBy
        );
        customerRepository.save(customer);

        log.info("[CRM][POPIA] PDF export generated: customer={} requestedBy={} tenant={}",
                customerId, requestedBy, tenantId);
    }

    // ── PDF construction ──────────────────────────────────────────────────────

    private void writeDocument(Customer customer, List<CustomerActivity> activities,
                               UUID requestedBy, OutputStream out) throws IOException {
        try (var pdf = new PdfDocument(new PdfWriter(out));
             var doc = new Document(pdf, PageSize.A4)) {

            doc.setMargins(40, 50, 40, 50);

            var bold    = PdfFontFactory.createFont("Helvetica-Bold");
            var regular = PdfFontFactory.createFont("Helvetica");

            addHeader(doc, bold);
            addExportMetadata(doc, requestedBy, activities.size(), bold, regular);
            addPersonalData(doc, customer, bold, regular);
            addProcessingHistory(doc, activities, bold, regular);
            addFooter(doc, regular);
        }
    }

    private void addHeader(Document doc, PdfFont bold) {
        var headerTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                .useAllAvailableWidth()
                .setBackgroundColor(NAVY)
                .setBorder(null)
                .setMarginBottom(20);

        var title = new Cell()
                .add(new Paragraph("POPIA Data Subject Export")
                        .setFont(bold).setFontSize(20).setFontColor(ColorConstants.WHITE))
                .add(new Paragraph("Protection of Personal Information Act — Section 23 request")
                        .setFontSize(10).setFontColor(new DeviceRgb(191, 219, 254)))
                .setBorder(null)
                .setPadding(16);

        headerTable.addCell(title);
        doc.add(headerTable);
    }

    private void addExportMetadata(Document doc, UUID requestedBy, int activityCount,
                                   PdfFont bold, PdfFont regular) {
        sectionHeading(doc, "Export Details", bold);

        var table = new Table(UnitValue.createPercentArray(new float[]{35, 65}))
                .useAllAvailableWidth()
                .setMarginBottom(16)
                .setBorderBottom(new SolidBorder(LIGHT, 1));

        row(table, "Generated", DATETIME_FMT.format(Instant.now()), bold, regular);
        row(table, "Requested by", resolveUserLabel(requestedBy), bold, regular);
        row(table, "Processing history entries", String.valueOf(activityCount), bold, regular);

        doc.add(table);
    }

    private void addPersonalData(Document doc, Customer customer, PdfFont bold, PdfFont regular) {
        sectionHeading(doc, "Personal Information Held", bold);

        var table = new Table(UnitValue.createPercentArray(new float[]{35, 65}))
                .useAllAvailableWidth()
                .setMarginBottom(16)
                .setBorderBottom(new SolidBorder(LIGHT, 1));

        row(table, "Name",        customer.getName(),        bold, regular);
        row(table, "Type",        customer.getCustomerType().name(), bold, regular);
        row(table, "Status",      customer.getStatus().name(),       bold, regular);
        row(table, "Email",       emptyIfNull(customer.getEmail()),  bold, regular);
        row(table, "Phone",       emptyIfNull(customer.getPhone()),  bold, regular);
        row(table, "VAT Number",  emptyIfNull(customer.getTaxNumber()), bold, regular);
        if (customer.getAddress() != null && !customer.getAddress().isEmpty()) {
            row(table, "Address", formatAddress(customer.getAddress()), bold, regular);
        }
        row(table, "Tags", customer.getTags() == null || customer.getTags().isEmpty()
                ? "—" : String.join(", ", customer.getTags()), bold, regular);
        row(table, "Notes", emptyIfNull(customer.getNotes()), bold, regular);
        row(table, "Record created",  customer.getCreatedAt() != null ? DATE_FMT.format(customer.getCreatedAt()) : "—", bold, regular);
        row(table, "Record updated",  customer.getUpdatedAt() != null ? DATE_FMT.format(customer.getUpdatedAt()) : "—", bold, regular);
        if (customer.getDeletedAt() != null) {
            row(table, "Record deleted", DATE_FMT.format(customer.getDeletedAt()), bold, regular);
        }

        doc.add(table);
    }

    private void addProcessingHistory(Document doc, List<CustomerActivity> activities,
                                      PdfFont bold, PdfFont regular) {
        sectionHeading(doc, "Complete Processing History (" + activities.size() + " entries)", bold);

        if (activities.isEmpty()) {
            doc.add(new Paragraph("No processing history recorded.")
                    .setFont(regular).setFontSize(11).setFontColor(MUTED).setMarginBottom(16));
            return;
        }

        var table = new Table(UnitValue.createPercentArray(new float[]{22, 25, 53}))
                .useAllAvailableWidth()
                .setMarginBottom(16);

        for (String h : List.of("Date", "Event", "Detail")) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(10))
                    .setBackgroundColor(LIGHT)
                    .setBorder(new SolidBorder(new DeviceRgb(226, 232, 240), 0.5f))
                    .setPadding(6));
        }

        for (var a : activities) {
            String date   = a.getCreatedAt() != null ? DATETIME_FMT.format(a.getCreatedAt()) : "—";
            String event  = friendlyLabel(a.getActivityType().name());
            String detail = buildDetail(a);

            for (String val : List.of(date, event, detail)) {
                table.addCell(new Cell()
                        .add(new Paragraph(val).setFont(regular).setFontSize(9))
                        .setBorder(new SolidBorder(new DeviceRgb(241, 245, 249), 0.5f))
                        .setPadding(6));
            }
        }
        doc.add(table);
    }

    private String buildDetail(CustomerActivity a) {
        if (a.getNote() != null && !a.getNote().isBlank()) return a.getNote();
        if (a.getPayload() != null && !a.getPayload().isEmpty()) {
            return a.getPayload().entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .reduce((x, y) -> x + "; " + y)
                    .orElse("");
        }
        return "";
    }

    private void addFooter(Document doc, PdfFont regular) {
        doc.add(new Paragraph(
                "This export was generated under POPIA Section 23 in response to a data subject access "
                        + "request. It includes all personal information held and the complete processing "
                        + "history for this record. Generated " + DATETIME_FMT.format(Instant.now())
                        + " · HandyFlow CRM · Confidential")
                .setFont(regular).setFontSize(9)
                .setFontColor(MUTED)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(20)
                .setBorderTop(new SolidBorder(LIGHT, 1)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void sectionHeading(Document doc, String title, PdfFont bold) {
        doc.add(new Paragraph(title)
                .setFont(bold).setFontSize(13)
                .setFontColor(NAVY)
                .setMarginBottom(6)
                .setBorderBottom(new SolidBorder(NAVY, 1.5f)));
    }

    private static void row(Table table, String label, String value, PdfFont bold, PdfFont regular) {
        table.addCell(new Cell()
                .add(new Paragraph(label).setFont(bold).setFontSize(10).setFontColor(MUTED))
                .setBorder(new SolidBorder(LIGHT, 0.5f))
                .setPadding(6));
        table.addCell(new Cell()
                .add(new Paragraph(value != null ? value : "—").setFont(regular).setFontSize(11))
                .setBorder(new SolidBorder(LIGHT, 0.5f))
                .setPadding(6));
    }

    private static String formatAddress(Map<String, String> addr) {
        return String.join(", ",
                addr.getOrDefault("street", ""),
                addr.getOrDefault("suburb", ""),
                addr.getOrDefault("city", ""),
                addr.getOrDefault("province", ""),
                addr.getOrDefault("postalCode", "")
        ).replaceAll("(, )+", ", ").replaceAll("^, |, $", "");
    }

    private static String friendlyLabel(String activityType) {
        return switch (activityType) {
            case "CREATED"                 -> "Customer created";
            case "UPDATED"                 -> "Details updated";
            case "DELETED"                 -> "Customer deleted";
            case "RESTORED"                -> "Customer restored";
            case "STATUS_CHANGED"          -> "Status changed";
            case "TAG_ADDED"               -> "Tag added";
            case "TAG_REMOVED"             -> "Tag removed";
            case "NOTE_ADDED"              -> "Note added";
            case "BOOKING_LINKED"          -> "Booking linked";
            case "INVOICE_LINKED"          -> "Invoice linked";
            case "QUOTE_LINKED"            -> "Quote linked";
            case "MARKETING_OPTED_IN"      -> "Marketing opt-in";
            case "MARKETING_OPTED_OUT"     -> "Marketing opt-out";
            case "RETENTION_REVIEW_REQUIRED" -> "Retention review required";
            default                        -> activityType;
        };
    }

    private static String emptyIfNull(String s) {
        return s != null ? s : "—";
    }

    /** Same jdbc.queryForObject pattern already confirmed working elsewhere in this codebase. */
    private String resolveUserLabel(UUID userId) {
        if (userId == null) return "—";
        try {
            String firstName = jdbc.queryForObject("SELECT first_name FROM users WHERE id = ?", String.class, userId);
            String lastName  = jdbc.queryForObject("SELECT last_name FROM users WHERE id = ?", String.class, userId);
            String name = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
            return name.isEmpty() ? userId.toString() : name;
        } catch (Exception e) {
            return userId.toString();
        }
    }
}