package za.co.handyflow.platform.fleet.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.Trip;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.repository.TripRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

// NOTE: org.apache.poi.ss.usermodel.Font is deliberately NOT imported above
// (not even by name) — it shares its simple name "Font" with
// com.lowagie.text.Font, which IS wildcard-imported for the PDF half of
// this class. Importing both under the same simple name is exactly what
// broke the build: javac resolved every unqualified "Font" to
// com.lowagie.text.Font, so the two Excel-styling methods below that
// actually need org.apache.poi.ss.usermodel.Font were trying to call
// setBold()/setFontHeightInPoints() on the wrong class entirely. Those two
// methods fully-qualify org.apache.poi.ss.usermodel.Font instead — see
// boldStyle() and headerStyle() below. Do not add a bare "import
// org.apache.poi.ss.usermodel.Font;" to this file for the same reason.

/**
 * SARS travel allowance logbook — PDF and Excel export.
 * <p>
 * WHY THIS EXISTS: the fleet gap analysis flagged this as the single
 * highest-value, lowest-lift item outstanding — the entire data model
 * (Business/Private trip classification, odometer readings, dates,
 * purposes) already existed and was already correct; nothing rolled it up
 * into the document SARS actually asks for at tax time. Every trip
 * classification a driver has ever made in {@code TripsTab.tsx} was
 * previously just... sitting there, unused for its actual purpose.
 * <p>
 * SCOPE: this covers ONE vehicle over a date range — matches how SARS
 * logbooks actually work (an individual claims a travel allowance for the
 * specific vehicle they used, not a fleet-wide report). Defaults to the
 * current South African tax year (1 March – end of February) if no range
 * is given, since that's the period SARS actually asks for.
 * <p>
 * LICENSING NOTE: built on OpenPDF (LGPL/MPL), not the itext7-core
 * dependency also present in this project's pom.xml — itext7-core is AGPL
 * unless a commercial license has been purchased, which is a real legal
 * obligation for a commercial SaaS product, not a style choice. Confirm
 * with whoever added itext7-core whether it's actually licensed and in use
 * before using it anywhere else.
 */
@Service
@RequiredArgsConstructor
public class FleetLogbookService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Font TITLE_FONT   = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(27, 58, 107));
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
    private static final Font BODY_FONT    = new Font(Font.HELVETICA, 9);
    private static final Font LABEL_FONT   = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(100, 116, 139));
    private static final Color BRAND_NAVY  = new Color(27, 58, 107);

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;

    // ── PDF ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generatePdf(TenantId tenantId, UUID vehicleId, LocalDate from, LocalDate to) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        LocalDate[] range = resolveRange(from, to);
        List<Trip> trips = fetchTrips(vehicleId, range[0], range[1]);
        LogbookSummary summary = LogbookSummary.from(trips);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document doc = new Document(PageSize.A4, 36, 36, 54, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            addHeader(doc, vehicle, range);
            addSummary(doc, summary);
            addTripTable(doc, trips);
            addDeclaration(doc);

            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate logbook PDF", e);
        }
        return out.toByteArray();
    }

    private void addHeader(Document doc, Vehicle vehicle, LocalDate[] range) throws DocumentException {
        Paragraph title = new Paragraph("Travel Logbook", TITLE_FONT);
        title.setSpacingAfter(2);
        doc.add(title);

        Paragraph subtitle = new Paragraph(
                "For SARS travel allowance purposes — Period: " + range[0].format(DATE_FMT)
                        + " to " + range[1].format(DATE_FMT),
                new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(100, 116, 139)));
        subtitle.setSpacingAfter(14);
        doc.add(subtitle);

        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setWidths(new float[]{1, 2});
        addInfoRow(info, "Registration", vehicle.getRegistration());
        addInfoRow(info, "Vehicle", vehicle.getMake() + " " + vehicle.getModel()
                + (vehicle.getYear() != null ? " (" + vehicle.getYear() + ")" : ""));
        addInfoRow(info, "Assigned driver", vehicle.getAssignedDriverName() != null
                ? vehicle.getAssignedDriverName() : "Not recorded");
        info.setSpacingAfter(16);
        doc.add(info);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, LABEL_FONT));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPaddingBottom(4);
        PdfPCell v = new PdfPCell(new Phrase(value, BODY_FONT));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPaddingBottom(4);
        table.addCell(l);
        table.addCell(v);
    }

    private void addSummary(Document doc, LogbookSummary s) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingAfter(18);

        addSummaryCell(table, "Opening odometer", s.openingOdometer() != null ? fmtKm(s.openingOdometer()) : "—");
        addSummaryCell(table, "Closing odometer", s.closingOdometer() != null ? fmtKm(s.closingOdometer()) : "—");
        addSummaryCell(table, "Total distance", fmtKm(s.totalKm()));
        addSummaryCell(table, "Business %", String.format("%.1f%%", s.businessPercent()));
        addSummaryCell(table, "Business km", fmtKm(s.businessKm()));
        addSummaryCell(table, "Private km", fmtKm(s.privateKm()));
        addSummaryCell(table, "Logged trips", String.valueOf(s.tripCount()));
        addSummaryCell(table, "", "");

        doc.add(table);
    }

    private void addSummaryCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new Color(248, 250, 252));
        cell.setBorderColor(new Color(226, 232, 240));
        cell.setPadding(8);
        if (!label.isEmpty()) {
            Paragraph p = new Paragraph();
            p.add(new Chunk(label.toUpperCase() + "\n", new Font(Font.HELVETICA, 7, Font.BOLD, new Color(148, 163, 184))));
            p.add(new Chunk(value, new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_NAVY)));
            cell.addElement(p);
        }
        table.addCell(cell);
    }

    private void addTripTable(Document doc, List<Trip> trips) throws DocumentException {
        if (trips.isEmpty()) {
            doc.add(new Paragraph("No completed trips recorded in this period.", BODY_FONT));
            return;
        }

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 1.2f, 3, 1.3f, 1.3f, 1.1f, 1.1f});
        table.setHeaderRows(1);

        for (String h : List.of("Date", "Type", "Purpose / Route", "Open km", "Close km", "Business", "Private")) {
            PdfPCell header = new PdfPCell(new Phrase(h, HEADING_FONT));
            header.setBackgroundColor(BRAND_NAVY);
            header.setPadding(6);
            header.setBorderColor(BRAND_NAVY);
            table.addCell(header);
        }

        ZoneId sast = ZoneId.of("Africa/Johannesburg");
        boolean shaded = false;
        for (Trip t : trips) {
            Color bg = shaded ? new Color(248, 250, 252) : Color.WHITE;
            shaded = !shaded;

            String date = t.getStartAt().atZone(sast).toLocalDate().format(DATE_FMT);
            String route = (t.getStartLocation() != null ? t.getStartLocation() : "—")
                    + " → " + (t.getEndLocation() != null ? t.getEndLocation() : "—")
                    + (t.getPurpose() != null ? " (" + t.getPurpose() + ")" : "");
            boolean business = !"PRIVATE".equalsIgnoreCase(t.getTripType());
            Integer km = t.getDistanceKm();

            addBodyCell(table, date, bg);
            addBodyCell(table, business ? "Business" : "Private", bg);
            addBodyCell(table, route, bg);
            addBodyCell(table, String.valueOf(t.getStartOdometer()), bg);
            addBodyCell(table, t.getEndOdometer() != null ? String.valueOf(t.getEndOdometer()) : "—", bg);
            addBodyCell(table, business && km != null ? String.valueOf(km) : "", bg);
            addBodyCell(table, !business && km != null ? String.valueOf(km) : "", bg);
        }
        doc.add(table);
    }

    private void addBodyCell(PdfPTable table, String text, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BODY_FONT));
        cell.setPadding(5);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(new Color(226, 232, 240));
        table.addCell(cell);
    }

    private void addDeclaration(Document doc) throws DocumentException {
        Paragraph decl = new Paragraph();
        decl.setSpacingBefore(24);
        decl.add(new Chunk("This logbook reflects trips recorded via HandyFlow's Fleet module for the period "
                + "stated above. Odometer readings and trip classifications were entered by the vehicle's "
                + "driver(s) at the time of travel.\n\n", new Font(Font.HELVETICA, 8, Font.ITALIC, new Color(148, 163, 184))));
        decl.add(new Chunk("Signed: _________________________        Date: _______________",
                new Font(Font.HELVETICA, 10)));
        doc.add(decl);
    }

    // ── Excel ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateExcel(TenantId tenantId, UUID vehicleId, LocalDate from, LocalDate to) {
        Vehicle vehicle = findVehicle(tenantId, vehicleId);
        LocalDate[] range = resolveRange(from, to);
        List<Trip> trips = fetchTrips(vehicleId, range[0], range[1]);
        LogbookSummary summary = LogbookSummary.from(trips);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Logbook");

            CellStyle titleStyle = boldStyle(wb, 14);
            CellStyle labelStyle = boldStyle(wb, 10);
            CellStyle headerStyle = headerStyle(wb);
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("dd mmm yyyy"));

            int r = 0;
            r = writeRow(sheet, r, titleStyle, "Travel Logbook — " + vehicle.getRegistration());
            r = writeRow(sheet, r, null, "Period: " + range[0].format(DATE_FMT) + " to " + range[1].format(DATE_FMT));
            r = writeRow(sheet, r, null, "Vehicle: " + vehicle.getMake() + " " + vehicle.getModel());
            r = writeRow(sheet, r, null, "Driver: " + (vehicle.getAssignedDriverName() != null ? vehicle.getAssignedDriverName() : "Not recorded"));
            r++;

            r = writeRow(sheet, r, labelStyle, "Opening odometer",
                    summary.openingOdometer() != null ? String.valueOf(summary.openingOdometer()) : "—");
            r = writeRow(sheet, r, labelStyle, "Closing odometer",
                    summary.closingOdometer() != null ? String.valueOf(summary.closingOdometer()) : "—");
            r = writeRow(sheet, r, labelStyle, "Total km", String.valueOf(summary.totalKm()));
            r = writeRow(sheet, r, labelStyle, "Business km", String.valueOf(summary.businessKm()));
            r = writeRow(sheet, r, labelStyle, "Private km", String.valueOf(summary.privateKm()));
            r = writeRow(sheet, r, labelStyle, "Business %", String.format("%.1f%%", summary.businessPercent()));
            r++;

            Row header = sheet.createRow(r++);
            String[] cols = {"Date", "Type", "Purpose", "From", "To", "Open km", "Close km", "Business km", "Private km"};
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
            }

            ZoneId sast = ZoneId.of("Africa/Johannesburg");
            for (Trip t : trips) {
                Row row = sheet.createRow(r++);
                boolean business = !"PRIVATE".equalsIgnoreCase(t.getTripType());
                Integer km = t.getDistanceKm();

                row.createCell(0).setCellValue(t.getStartAt().atZone(sast).toLocalDate().format(DATE_FMT));
                row.createCell(1).setCellValue(business ? "Business" : "Private");
                row.createCell(2).setCellValue(t.getPurpose() != null ? t.getPurpose() : "");
                row.createCell(3).setCellValue(t.getStartLocation() != null ? t.getStartLocation() : "");
                row.createCell(4).setCellValue(t.getEndLocation() != null ? t.getEndLocation() : "");
                row.createCell(5).setCellValue(t.getStartOdometer());
                if (t.getEndOdometer() != null) row.createCell(6).setCellValue(t.getEndOdometer());
                if (business && km != null) row.createCell(7).setCellValue(km);
                if (!business && km != null) row.createCell(8).setCellValue(km);
            }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to generate logbook Excel", e);
        }
    }

    private int writeRow(Sheet sheet, int rowIndex, CellStyle style, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            if (style != null) cell.setCellStyle(style);
        }
        return rowIndex + 1;
    }

    private CellStyle boldStyle(Workbook wb, int size) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) size);
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Vehicle findVehicle(TenantId tenantId, UUID vehicleId) {
        return vehicleRepository.findActiveById(tenantId, vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId.toString()));
    }

    private List<Trip> fetchTrips(UUID vehicleId, LocalDate from, LocalDate to) {
        ZoneId sast = ZoneId.of("Africa/Johannesburg");
        var fromInstant = from.atStartOfDay(sast).toInstant();
        var toInstant = to.plusDays(1).atStartOfDay(sast).toInstant(); // exclusive upper bound, so 'to' day is included
        return tripRepository.findCompletedInRange(vehicleId, fromInstant, toInstant);
    }

    /**
     * Defaults to the current South African tax year (1 March – end of
     * February) when no explicit range is given — that's the period SARS
     * actually asks for, so it's the sensible default rather than "last 30
     * days" or similar.
     */
    private LocalDate[] resolveRange(LocalDate from, LocalDate to) {
        if (from != null && to != null) return new LocalDate[]{from, to};

        LocalDate today = LocalDate.now();
        LocalDate marchFirstThisYear = LocalDate.of(today.getYear(), 3, 1);
        int startYear = today.isBefore(marchFirstThisYear) ? today.getYear() - 1 : today.getYear();
        LocalDate start = LocalDate.of(startYear, 3, 1);
        LocalDate end = YearMonth.of(startYear + 1, 2).atEndOfMonth();
        return new LocalDate[]{start, end};
    }

    private String fmtKm(int km) {
        return String.format("%,d km", km);
    }
}