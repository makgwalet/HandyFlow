package za.co.handyflow.platform.bookings.application.internal;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
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
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.bookings.domain.model.Booking;
import za.co.handyflow.platform.bookings.domain.model.BookingService;
import za.co.handyflow.platform.bookings.domain.repository.BookingServiceRepository;
import za.co.handyflow.platform.bookings.domain.repository.BookingStaffRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * BookingConfirmationPdfService — generates a booking confirmation PDF.
 *
 * WHY generate a PDF and not just rely on the HTML email?
 * Three reasons:
 *
 * 1. Printable.  A hair salon client might print their confirmation to hand to
 *    the receptionist.  A property inspection client might attach it to a
 *    conveyancer email.  HTML email is not printable in a controlled way.
 *
 * 2. Archivable.  PDFs can be saved to Google Drive, iCloud, or a physical
 *    folder.  Clients appreciate a document they can retrieve months later.
 *
 * 3. Professional.  A branded, structured PDF signals that the business is
 *    serious — the same reason invoices are PDF, not plain email text.
 *
 * WHY return byte[] and not stream to a file?
 * The PDF is attached to an email and discarded — it's never written to disk.
 * Keeping it in memory as byte[] avoids file system permissions, temp file
 * cleanup, and disk I/O.  At an average size of 20–50KB per confirmation PDF,
 * heap pressure is negligible.
 *
 * WHY iText 7 and not JasperReports?
 * See CustomerProfilePdfService for the full rationale.  Short version: iText
 * is explicit, programmatic, and has zero template files to manage.
 *
 * LICENCE: iText 7 Community is AGPL.  For commercial use, purchase iText
 * commercial licence or swap to OpenPDF (LGPL, API ~98% compatible).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingConfirmationPdfService {

    private static final ZoneId         SAST     = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("EEEE, d MMMM yyyy").withZone(SAST);
    private static final DateTimeFormatter GEN_FMT  = DateTimeFormatter
            .ofPattern("d MMM yyyy HH:mm").withZone(SAST);

    private static final DeviceRgb NAVY  = new DeviceRgb(27,  58, 107);
    private static final DeviceRgb GREEN = new DeviceRgb(22, 101,  52);
    private static final DeviceRgb LIGHT = new DeviceRgb(239, 246, 255);
    private static final DeviceRgb MUTED = new DeviceRgb(100, 116, 139);

    private final BookingServiceRepository serviceRepo;
    private final BookingStaffRepository   staffRepo;

    /**
     * Generates a booking confirmation PDF and returns the bytes.
     * Called by BookingsService.confirmBooking() after the status is updated.
     *
     * @param tenantId  Current tenant
     * @param booking   The just-confirmed Booking entity
     * @return          PDF bytes ready to attach to an email
     */
    public byte[] generate(TenantId tenantId, Booking booking) throws IOException {
        String serviceName = serviceRepo.findActiveById(tenantId, booking.getServiceId())
                .map(BookingService::getName)
                .orElse("Appointment");

        var out = new ByteArrayOutputStream();
        try (var pdf = new PdfDocument(new PdfWriter(out));
             var doc = new Document(pdf, PageSize.A5)) {  // A5 = compact, single page

            doc.setMargins(36, 44, 36, 44);

            var bold    = PdfFontFactory.createFont("Helvetica-Bold");
            var regular = PdfFontFactory.createFont("Helvetica");

            // ── Header ─────────────────────────────────────────────────────────
            var header = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .useAllAvailableWidth()
                    .setBackgroundColor(NAVY)
                    .setMarginBottom(18);

            header.addCell(new Cell()
                    .add(new Paragraph("Booking Confirmed")
                            .setFont(bold).setFontSize(18).setFontColor(ColorConstants.WHITE))
                    .add(new Paragraph("HandyFlow")
                            .setFontSize(10).setFontColor(new DeviceRgb(191, 219, 254)))
                    .setBorder(null).setPadding(14));
            doc.add(header);

            // ── Green confirmation badge ────────────────────────────────────────
            doc.add(new Paragraph("✓  Your appointment is confirmed")
                    .setFont(bold).setFontSize(12)
                    .setFontColor(GREEN)
                    .setBackgroundColor(new DeviceRgb(240, 253, 244))
                    .setBorder(new SolidBorder(new DeviceRgb(134, 239, 172), 1))
                    .setPadding(10).setMarginBottom(16));

            // ── Details table ──────────────────────────────────────────────────
            var table = new Table(UnitValue.createPercentArray(new float[]{38, 62}))
                    .useAllAvailableWidth()
                    .setMarginBottom(16);

            addRow(table, "Booking ref",  booking.getBookingNumber(), bold, regular);
            addRow(table, "Service",      serviceName,                bold, regular);
            addRow(table, "Date",         DATE_FMT.format(booking.getBookingDate().atStartOfDay(SAST)), bold, regular);
            addRow(table, "Time",         booking.getStartTime().toString().substring(0, 5)
                    + " – " + booking.getEndTime().toString().substring(0, 5), bold, regular);
            if (booking.getStaffId() != null) {
                String staffName = staffRepo.findByTenantAndId(tenantId, booking.getStaffId())
                        .map(za.co.handyflow.platform.bookings.domain.model.BookingStaff::getName)
                        .orElse("Your assigned specialist");
                addRow(table, "With", staffName, bold, regular);
            }
            if (booking.getPrice() != null && booking.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                addRow(table, "Price", "R " + booking.getPrice().toPlainString(), bold, regular);
            }
            if (booking.getNotes() != null && !booking.getNotes().isBlank()) {
                addRow(table, "Notes", booking.getNotes(), bold, regular);
            }
            doc.add(table);

            // ── Instructions ───────────────────────────────────────────────────
            doc.add(new Paragraph(
                    "Please arrive 5 minutes before your scheduled time. " +
                            "If you need to cancel or reschedule, contact us as soon as possible " +
                            "so we can offer the slot to another client.")
                    .setFont(regular).setFontSize(10)
                    .setFontColor(MUTED)
                    .setMarginBottom(14));

            // ── Footer ─────────────────────────────────────────────────────────
            doc.add(new Paragraph("Generated " + GEN_FMT.format(java.time.Instant.now())
                    + " · HandyFlow · Booking " + booking.getBookingNumber())
                    .setFont(regular).setFontSize(8)
                    .setFontColor(MUTED)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBorderTop(new SolidBorder(LIGHT, 1))
                    .setMarginTop(10));
        }

        log.info("[Bookings] Confirmation PDF generated booking={} size={}B",
                booking.getBookingNumber(), out.size());
        return out.toByteArray();
    }

    private static void addRow(Table t, String label, String value,
                               com.itextpdf.kernel.font.PdfFont bold,
                               com.itextpdf.kernel.font.PdfFont regular) {
        t.addCell(new Cell()
                .add(new Paragraph(label).setFont(bold).setFontSize(10).setFontColor(MUTED))
                .setBorder(new SolidBorder(LIGHT, 0.5f)).setPadding(6));
        t.addCell(new Cell()
                .add(new Paragraph(value != null ? value : "—").setFont(regular).setFontSize(11))
                .setBorder(new SolidBorder(LIGHT, 0.5f)).setPadding(6));
    }
}
