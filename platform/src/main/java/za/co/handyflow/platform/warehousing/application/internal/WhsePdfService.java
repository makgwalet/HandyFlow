package za.co.handyflow.platform.warehousing.application.internal;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocation;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrder;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrderLine;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Warehousing document generation. Built on OpenPDF (com.lowagie.text.*),
 * same structure/brand colors/footer technique as every other PDF service
 * in this codebase (e.g. CollAgencyPdfService) — see that class's own
 * Javadoc for why OpenPDF and not iText7/OpenHTMLtoPDF.
 * <p>
 * Two documents:
 * <ul>
 *   <li>{@link #generateInventoryStatement} — a client's current stock
 *   position across every location, the downloadable/printable version of
 *   the same data the client portal's own inventory view is built from
 *   (see WhsePortalDataService.getMyInventory()).</li>
 *   <li>{@link #generatePackingSlip} — a delivery note for one outbound
 *   order: what was ordered, where it shipped to, and (once shipped)
 *   carrier/tracking details. This is the module's proof-of-fulfilment
 *   document — a client-facing companion to the physical shipment, and
 *   a natural candidate to also attach as Evidence against the order (see
 *   the API layer's evidence wiring).</li>
 * </ul>
 */
@Slf4j
@Component
public class WhsePdfService {

    private static final Color BRAND_COLOR = new Color(30, 41, 59);
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.WHITE);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font TABLE_CELL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FOOTER_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11);
    private static final Font BODY_BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] generateInventoryStatement(String warehouseName, WhseClient client, List<WhseInventory> positions,
                                              Map<java.util.UUID, WhseItem> itemsById,
                                              Map<java.util.UUID, WhseLocation> locationsById) {
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 60, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterHandler("Inventory Statement — " + client.getTradingName()));
            doc.open();
            addHeader(doc, warehouseName, "Inventory Statement — " + client.getTradingName(), positions.size());

            String[] headers = {"SKU", "Description", "Location", "Qty On Hand", "Qty Allocated", "Available"};
            float[] widths = {1.3f, 2.4f, 1.2f, 1.2f, 1.2f, 1.2f};
            PdfPTable table = new PdfPTable(widths);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
                cell.setBackgroundColor(BRAND_COLOR);
                cell.setPadding(5);
                table.addCell(cell);
            }
            for (WhseInventory p : positions) {
                WhseItem item = itemsById.get(p.getItemId());
                WhseLocation location = locationsById.get(p.getLocationId());
                for (String value : new String[]{
                        item != null ? nullSafe(item.getSku()) : p.getItemId().toString(),
                        item != null ? nullSafe(item.getDescription()) : "",
                        location != null ? nullSafe(location.getCode()) : p.getLocationId().toString(),
                        p.getQtyOnHand().toPlainString(),
                        p.getQtyAllocated().toPlainString(),
                        p.available().toPlainString()
                }) {
                    PdfPCell cell = new PdfPCell(new Phrase(value, TABLE_CELL_FONT));
                    cell.setPadding(4);
                    table.addCell(cell);
                }
            }
            if (positions.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("No stock currently held", TABLE_CELL_FONT));
                empty.setColspan(headers.length);
                empty.setPadding(8);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(empty);
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate inventory statement for client {}: {}", client.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate inventory statement", e);
        }
    }

    public byte[] generatePackingSlip(String warehouseName, WhseClient client, WhseOutboundOrder order,
                                       List<WhseOutboundOrderLine> lines, Map<java.util.UUID, WhseItem> itemsById) {
        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            String reportTitle = "Packing Slip — " + nullSafe(order.getOrderReference());
            writer.setPageEvent(new FooterHandler(reportTitle));
            doc.open();

            PdfPTable header = new PdfPTable(1);
            header.setWidthPercentage(100);
            PdfPCell headerCell = new PdfPCell();
            headerCell.setBackgroundColor(BRAND_COLOR);
            headerCell.setPadding(12);
            headerCell.setBorder(0);
            headerCell.addElement(new Paragraph(reportTitle, TITLE_FONT));
            headerCell.addElement(new Paragraph(
                    (warehouseName != null ? warehouseName : "HandyFlow") + " · " + client.getTradingName()
                            + " · generated " + LocalDate.now().format(DATE_FMT), SUBTITLE_FONT));
            header.addCell(headerCell);
            doc.add(header);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Ship To", SECTION_FONT));
            doc.add(new Paragraph(nullSafe(order.getShipToName()), BODY_BOLD_FONT));
            if (order.getShipToAddress() != null) doc.add(new Paragraph(order.getShipToAddress(), BODY_FONT));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Order Details", SECTION_FONT));
            doc.add(new Paragraph("Status: " + order.getStatus(), BODY_FONT));
            if (order.getRequestedShipDate() != null) {
                doc.add(new Paragraph("Requested ship date: " + order.getRequestedShipDate().format(DATE_FMT), BODY_FONT));
            }
            if (order.getCarrier() != null) doc.add(new Paragraph("Carrier: " + order.getCarrier(), BODY_FONT));
            if (order.getTrackingNumber() != null) doc.add(new Paragraph("Tracking: " + order.getTrackingNumber(), BODY_FONT));
            doc.add(new Paragraph(" "));

            String[] headers = {"SKU", "Description", "Qty Ordered", "Qty Picked"};
            float[] widths = {1.2f, 2.4f, 1.0f, 1.0f};
            PdfPTable table = new PdfPTable(widths);
            table.setWidthPercentage(100);
            table.setSpacingBefore(6);
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
                cell.setBackgroundColor(BRAND_COLOR);
                cell.setPadding(5);
                table.addCell(cell);
            }
            for (WhseOutboundOrderLine line : lines) {
                WhseItem item = itemsById.get(line.getItemId());
                for (String value : new String[]{
                        item != null ? nullSafe(item.getSku()) : line.getItemId().toString(),
                        item != null ? nullSafe(item.getDescription()) : "",
                        line.getQtyOrdered().toPlainString(),
                        line.getQtyPicked().toPlainString()
                }) {
                    PdfPCell cell = new PdfPCell(new Phrase(value, TABLE_CELL_FONT));
                    cell.setPadding(4);
                    table.addCell(cell);
                }
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate packing slip for order {}: {}", order.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate packing slip", e);
        }
    }

    private void addHeader(Document doc, String warehouseName, String reportTitle, int rowCount) throws Exception {
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BRAND_COLOR);
        cell.setPadding(12);
        cell.setBorder(0);
        cell.addElement(new Paragraph(reportTitle, TITLE_FONT));
        cell.addElement(new Paragraph(
                (warehouseName != null ? warehouseName : "HandyFlow") + " · " + rowCount + " position(s) · generated "
                        + LocalDate.now().format(DATE_FMT), SUBTITLE_FONT));
        header.addCell(cell);
        doc.add(header);
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    private static class FooterHandler extends PdfPageEventHelper {
        private final String reportTitle;

        FooterHandler(String reportTitle) {
            this.reportTitle = reportTitle;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                Phrase footer = new Phrase(
                        "HandyFlow · " + reportTitle + " · Page " + writer.getPageNumber(), FOOTER_FONT);
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, footer,
                        document.leftMargin(), document.bottomMargin() - 20, 0);
            } catch (Exception ignored) {
                // A broken footer must never take down PDF generation for an otherwise-valid document.
            }
        }
    }
}
