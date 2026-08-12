package za.co.handyflow.platform.events.application.internal;

import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventGuest;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * EventTicketPdfService — branded, printable/shareable event ticket with
 * an embedded QR code. This closes the audit's highest-severity finding
 * for this module: guests were registered but had no way to actually
 * receive a usable ticket (HandyFlow BOS Discovery doc, Section 61/76,
 * the Events module review).
 * <p>
 * FIX (Section 77): first version of this file guessed Event's getter
 * names (getName/getStartDate/getVenue) rather than confirming them —
 * all three were wrong, caught by the real compiler, not by review.
 * Corrected here against the actual entity: getTitle(), getVenueName(),
 * getStartDatetime() (returns LocalDateTime, not a date/time-split type —
 * DateTimeFormatter.format() works on LocalDateTime directly, no
 * conversion needed). Also added the missing Border import — used
 * Border.NO_BORDER without importing the base interface it lives on,
 * only SolidBorder.
 * <p>
 * QR GENERATION: uses iText's own BarcodeQRCode — NOT a new dependency.
 * Confirmed against security.application.internal.CheckpointQrPdfService,
 * which already proves this exact API works in this codebase's pinned
 * iText version. Same "createFormXObject(Color, PdfDocument)" call shape
 * copied from that confirmed-working reference, not guessed independently.
 * <p>
 * QR PAYLOAD: encodes EventGuest.qrCode directly — the same value
 * EventsService.checkIn() already looks up via
 * EventGuestRepository.findByQrCode(). No HMAC signing layer (unlike
 * Security's checkpoint QRs) — ticket fraud is a real but lower-stakes
 * risk here than a guard-post checkpoint, and check-in already marks a
 * ticket used, so a duplicate scan fails naturally. If that risk profile
 * changes, HMAC-signing the payload the same way Security does is the
 * proven pattern to copy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventTicketPdfService {

    private static final DeviceRgb BRAND_NAVY = new DeviceRgb(27, 58, 107);
    private static final DeviceRgb MID_GREY   = new DeviceRgb(148, 163, 184);
    private static final DeviceRgb LIGHT_BG   = new DeviceRgb(248, 250, 252);

    private static final float QR_SIZE_PT = 130f;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public byte[] generateTicket(Event event, EventGuest guest, String tierName) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
            Document doc = new Document(pdf, PageSize.A5); // ticket-sized, not full A4
            doc.setMargins(28, 28, 28, 28);

            addHeader(doc, event);
            addGuestDetails(doc, event, guest, tierName);
            addQrSection(doc, guest);
            addFooter(doc, guest);

            doc.close();
        } catch (Exception e) {
            log.error("[Events] Ticket PDF generation failed guest={} event={}: {}",
                    guest.getId(), event.getEventNumber(), e.getMessage(), e);
            throw new RuntimeException("Ticket PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    private void addHeader(Document doc, Event event) {
        Paragraph brand = new Paragraph("HandyFlow Events")
                .setFontColor(MID_GREY).setFontSize(9).setBold();
        doc.add(brand);

        // FIX: was event.getName() — real getter is getTitle()
        Paragraph title = new Paragraph(event.getTitle())
                .setFontColor(BRAND_NAVY).setFontSize(22).setBold().setMarginTop(4).setMarginBottom(2);
        doc.add(title);

        // FIX: was event.getStartDate() — real getter is getStartDatetime(),
        // returns LocalDateTime (not split date/time types). DateTimeFormatter
        // works directly against LocalDateTime, no conversion needed.
        if (event.getStartDatetime() != null) {
            String whenLine = event.getStartDatetime().format(DATE_FMT) + " at " + event.getStartDatetime().format(TIME_FMT);
            doc.add(new Paragraph(whenLine).setFontSize(11).setFontColor(MID_GREY).setMarginBottom(1));
        }
        // FIX: was event.getVenue() — real getter is getVenueName()
        if (event.getVenueName() != null && !event.getVenueName().isBlank()) {
            doc.add(new Paragraph(event.getVenueName()).setFontSize(11).setFontColor(MID_GREY).setMarginBottom(8));
        }

        doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1f))
                .setStrokeColor(new DeviceRgb(226, 232, 240)).setMarginBottom(10));
    }

    private void addGuestDetails(Document doc, Event event, EventGuest guest, String tierName) {
        Table info = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
        info.setMarginBottom(14);

        addInfoCell(info, "GUEST", guest.getFullName());
        addInfoCell(info, "TICKET #", guest.getTicketNumber());
        if (tierName != null) {
            addInfoCell(info, "TIER", tierName);
        }
        if (guest.getCompany() != null && !guest.getCompany().isBlank()) {
            addInfoCell(info, "COMPANY", guest.getCompany());
        }
        doc.add(info);
    }

    private void addInfoCell(Table table, String label, String value) {
        Cell cell = new Cell().setBorder(Border.NO_BORDER).setPadding(4);
        cell.add(new Paragraph(label).setFontSize(8).setFontColor(MID_GREY).setBold().setMarginBottom(2));
        cell.add(new Paragraph(value != null ? value : "—").setFontSize(12).setFontColor(ColorConstants.BLACK));
        table.addCell(cell);
    }

    private void addQrSection(Document doc, EventGuest guest) {
        BarcodeQRCode qr = new BarcodeQRCode(guest.getQrCode());
        PdfFormXObject qrObject = qr.createFormXObject(ColorConstants.BLACK, doc.getPdfDocument());
        Image qrImage = new Image(qrObject).setWidth(QR_SIZE_PT).setHeight(QR_SIZE_PT);

        Div qrBlock = new Div()
                .setBackgroundColor(LIGHT_BG)
                .setBorder(new SolidBorder(new DeviceRgb(226, 232, 240), 1f))
                .setPadding(16)
                .setTextAlignment(TextAlignment.CENTER)
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setMarginBottom(12);
        qrBlock.add(qrImage.setHorizontalAlignment(HorizontalAlignment.CENTER));
        qrBlock.add(new Paragraph("Present this code at the door")
                .setFontSize(9).setFontColor(MID_GREY).setMarginTop(6).setTextAlignment(TextAlignment.CENTER));
        doc.add(qrBlock);
    }

    private void addFooter(Document doc, EventGuest guest) {
        doc.add(new Paragraph("Ticket " + guest.getTicketNumber() + " — valid for one entry only")
                .setFontSize(8).setFontColor(MID_GREY).setTextAlignment(TextAlignment.CENTER).setMarginTop(8));
    }
}