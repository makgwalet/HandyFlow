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
import za.co.handyflow.platform.warehousing.domain.model.WhseBillingInvoice;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocation;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrder;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrderLine;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
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

            String[] headers = {"SKU", "Description", "Qty Ordered", "Qty Picked", "Short"};
            float[] widths = {1.2f, 2.2f, 0.9f, 0.9f, 0.8f};
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
                        line.getQtyPicked().toPlainString(),
                        line.getQtyShort().signum() > 0 ? line.getQtyShort().toPlainString() : "-"
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

    /** One row of the invoice's amounts table. {@code emphasis} rows (totals) are drawn bold. */
    record InvoiceLine(String label, BigDecimal amount, boolean emphasis) {}

    /**
     * The amounts on an invoice, in order. The storage fee is shown as stored goods plus, when the contract minimum
     * lifted it, a separate "minimum charge top-up" line, so the client can see why storage is more than the goods held.
     */
    static List<InvoiceLine> invoiceLines(WhseBillingInvoice inv) {
        BigDecimal topUp = inv.getStorageMinimumTopup() == null ? BigDecimal.ZERO : inv.getStorageMinimumTopup();
        BigDecimal positionFees = inv.getStorageLocationFees() == null ? BigDecimal.ZERO : inv.getStorageLocationFees();
        List<InvoiceLine> lines = new ArrayList<>();
        BigDecimal goods = inv.getStorageFee().subtract(topUp).subtract(positionFees);
        if (topUp.signum() > 0 || positionFees.signum() > 0) {
            lines.add(new InvoiceLine("Storage of goods held", goods, false));
            if (positionFees.signum() > 0) lines.add(new InvoiceLine("Pallet / bin position fees", positionFees, false));
            if (topUp.signum() > 0) lines.add(new InvoiceLine("Storage minimum charge top-up (contract minimum)", topUp, false));
        } else {
            lines.add(new InvoiceLine("Storage", inv.getStorageFee(), false));
        }
        lines.add(new InvoiceLine("Handling", inv.getHandlingFee(), false));
        lines.add(new InvoiceLine("Subtotal", inv.getSubtotal(), true));
        lines.add(new InvoiceLine("VAT", inv.getVatAmount(), false));
        lines.add(new InvoiceLine("Total", inv.getTotal(), true));
        BigDecimal paid = inv.getAmountPaid() == null ? BigDecimal.ZERO : inv.getAmountPaid();
        if (paid.signum() > 0) {
            lines.add(new InvoiceLine("Paid", paid, false));
            lines.add(new InvoiceLine("Balance due", inv.getTotal().subtract(paid), true));
        }
        return lines;
    }

    public byte[] generateBillingInvoice(String warehouseName, String warehouseRegistration, WhseClient client, WhseBillingInvoice inv) {
        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            String title = "Invoice " + nullSafe(inv.getInvoiceNumber());
            writer.setPageEvent(new FooterHandler(title));
            doc.open();

            PdfPTable header = new PdfPTable(1);
            header.setWidthPercentage(100);
            PdfPCell headerCell = new PdfPCell();
            headerCell.setBackgroundColor(BRAND_COLOR);
            headerCell.setPadding(12);
            headerCell.setBorder(0);
            headerCell.addElement(new Paragraph(title, TITLE_FONT));
            headerCell.addElement(new Paragraph((warehouseName != null ? warehouseName : "HandyFlow")
                    + (warehouseRegistration != null && !warehouseRegistration.isBlank() ? " · Reg " + warehouseRegistration : ""), SUBTITLE_FONT));
            header.addCell(headerCell);
            doc.add(header);
            doc.add(new Paragraph(" "));

            if ("DRAFT".equals(inv.getStatus()) || "REJECTED".equals(inv.getStatus())) {
                // Not an issued tax document until a second person has approved it.
                doc.add(new Paragraph(("REJECTED".equals(inv.getStatus()) ? "REJECTED" : "DRAFT") + " — NOT YET APPROVED, NOT A TAX INVOICE",
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(180, 40, 40))));
                doc.add(new Paragraph(" "));
            }

            doc.add(new Paragraph("Billed to", SECTION_FONT));
            doc.add(new Paragraph(nullSafe(client.getTradingName()), BODY_BOLD_FONT));
            if (client.getRegistrationNumber() != null) doc.add(new Paragraph("Reg " + client.getRegistrationNumber(), BODY_FONT));
            if (client.getAddress() != null) doc.add(new Paragraph(client.getAddress(), BODY_FONT));
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Invoice date: " + inv.getInvoiceDate().format(DATE_FMT), BODY_FONT));
            doc.add(new Paragraph("Period: " + inv.getPeriodStart().format(DATE_FMT) + " to " + inv.getPeriodEnd().format(DATE_FMT), BODY_FONT));
            doc.add(new Paragraph("Due: " + inv.getDueDate().format(DATE_FMT), BODY_FONT));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[]{3.4f, 1.2f});
            table.setWidthPercentage(100);
            for (String h : new String[]{"Description", "Amount (ZAR)"}) {
                PdfPCell cell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
                cell.setBackgroundColor(BRAND_COLOR);
                cell.setPadding(5);
                if (!"Description".equals(h)) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(cell);
            }
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            for (InvoiceLine l : invoiceLines(inv)) {
                PdfPCell label = new PdfPCell(new Phrase(l.label(), l.emphasis() ? bold : TABLE_CELL_FONT));
                label.setPadding(4);
                PdfPCell amount = new PdfPCell(new Phrase(l.amount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), l.emphasis() ? bold : TABLE_CELL_FONT));
                amount.setPadding(4);
                amount.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(label);
                table.addCell(amount);
            }
            doc.add(table);
            BigDecimal topUp = inv.getStorageMinimumTopup() == null ? BigDecimal.ZERO : inv.getStorageMinimumTopup();
            if (topUp.signum() > 0) {
                doc.add(new Paragraph(" "));
                doc.add(new Paragraph("The top-up brings this period's storage up to the minimum storage charge agreed for your account.", FOOTER_FONT));
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate invoice {}: {}", inv.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate invoice", e);
        }
    }

    /** The dispatch manifest: what is on the vehicle, order by order, with sign-off lines for the dispatcher and the driver. */
    public byte[] generateManifest(String warehouseName, WhseDispatchService.ManifestDetail detail) {
        WhseDispatchService.ManifestRow m = detail.manifest();
        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            String title = "Dispatch Manifest " + nullSafe(m.manifestNumber());
            writer.setPageEvent(new FooterHandler(title));
            doc.open();

            PdfPTable header = new PdfPTable(1);
            header.setWidthPercentage(100);
            PdfPCell headerCell = new PdfPCell();
            headerCell.setBackgroundColor(BRAND_COLOR);
            headerCell.setPadding(12);
            headerCell.setBorder(0);
            headerCell.addElement(new Paragraph(title, TITLE_FONT));
            headerCell.addElement(new Paragraph((warehouseName != null ? warehouseName : "HandyFlow") + " · status " + m.status(), SUBTITLE_FONT));
            header.addCell(headerCell);
            doc.add(header);
            doc.add(new Paragraph(" "));

            doc.add(new Paragraph("Carrier: " + (m.carrier() == null ? "-" : m.carrier()), BODY_FONT));
            doc.add(new Paragraph("Vehicle: " + (m.vehicleRegistration() == null ? "-" : m.vehicleRegistration()), BODY_FONT));
            doc.add(new Paragraph("Driver: " + (m.driverName() == null ? "-" : m.driverName()), BODY_FONT));
            if (m.notes() != null) doc.add(new Paragraph("Notes: " + m.notes(), BODY_FONT));
            doc.add(new Paragraph(detail.orders().size() + " order(s) · " + detail.orders().stream().map(WhseDispatchService.OrderRow::units)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).stripTrailingZeros().toPlainString() + " unit(s)", BODY_BOLD_FONT));
            doc.add(new Paragraph(" "));

            for (WhseDispatchService.OrderRow o : detail.orders()) {
                doc.add(new Paragraph(nullSafe(o.orderReference()) + " · " + nullSafe(o.clientName()), SECTION_FONT));
                doc.add(new Paragraph("Deliver to: " + nullSafe(o.shipToName()) + (o.shipToAddress() == null ? "" : ", " + o.shipToAddress()), BODY_FONT));
                PdfPTable table = new PdfPTable(new float[]{1.3f, 2.8f, 0.9f});
                table.setWidthPercentage(100);
                table.setSpacingBefore(4);
                for (String h : new String[]{"SKU", "Description", "Qty"}) {
                    PdfPCell cell = new PdfPCell(new Phrase(h, TABLE_HEADER_FONT));
                    cell.setBackgroundColor(BRAND_COLOR);
                    cell.setPadding(4);
                    table.addCell(cell);
                }
                for (WhseDispatchService.LineRow l : o.lines()) {
                    for (String v : new String[]{nullSafe(l.sku()), nullSafe(l.description()), l.qty().stripTrailingZeros().toPlainString()}) {
                        PdfPCell cell = new PdfPCell(new Phrase(v, TABLE_CELL_FONT));
                        cell.setPadding(3);
                        table.addCell(cell);
                    }
                }
                doc.add(table);
                doc.add(new Paragraph(" "));
            }

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Loaded and checked by (warehouse): ______________________   Date / time: ______________", BODY_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Received on vehicle by (driver): ______________________   Signature: ______________", BODY_FONT));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate manifest {}: {}", m.id(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate manifest", e);
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

    /** A 100 x 150 mm shipping label for one package: where it goes, which package of how many, the weight and a Code 128 barcode of order/package. */
    public byte[] generatePackageLabel(WhsePackingService.LabelData d) {
        Document doc = new Document(new com.lowagie.text.Rectangle(283.5f, 425.2f), 14, 14, 14, 14);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();
            Font big = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.BLACK);
            Font mid = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            doc.add(new Paragraph("SHIP TO", small));
            doc.add(new Paragraph(nullSafe(d.shipToName()), mid));
            if (d.shipToAddress() != null) doc.add(new Paragraph(d.shipToAddress(), small));
            doc.add(new Paragraph(" ", small));
            doc.add(new Paragraph("Package " + d.packageNo() + " of " + d.packageCount(), big));
            doc.add(new Paragraph(d.type() + " · " + d.grossKg().stripTrailingZeros().toPlainString() + " kg"
                    + (d.lengthMm() != null && d.widthMm() != null && d.heightMm() != null ? " · " + d.lengthMm() + "x" + d.widthMm() + "x" + d.heightMm() + " mm" : ""), small));
            doc.add(new Paragraph(" ", small));
            doc.add(new Paragraph("Order " + nullSafe(d.orderReference()), mid));
            doc.add(new Paragraph("From " + nullSafe(d.clientName()), small));
            doc.add(new Paragraph(" ", small));
            for (WhsePackingService.PackedItem it : d.items()) {
                doc.add(new Paragraph(it.qty().stripTrailingZeros().toPlainString() + " × " + it.sku(), small));
            }
            com.lowagie.text.pdf.Barcode128 bc = new com.lowagie.text.pdf.Barcode128();
            bc.setCode(nullSafe(d.orderReference()) + "/P" + d.packageNo());
            bc.setCodeType(com.lowagie.text.pdf.Barcode128.CODE128);
            bc.setBarHeight(40f);
            com.lowagie.text.Image img = bc.createImageWithBarcode(writer.getDirectContent(), Color.BLACK, Color.BLACK);
            doc.add(new Paragraph(" ", small));
            doc.add(img);
            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate package label", e);
        }
        return out.toByteArray();
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
