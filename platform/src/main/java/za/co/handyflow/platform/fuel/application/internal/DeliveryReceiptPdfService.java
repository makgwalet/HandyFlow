// fuel/application/internal/DeliveryReceiptPdfService.java

package za.co.handyflow.platform.fuel.application.internal;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryReceiptPdfService {

    private final FuelDeliveryRepository deliveryRepository;
    private final TenantFacade           tenantFacade;

    private static final DeviceRgb NAVY       = new DeviceRgb(0x1B, 0x3A, 0x6B);
    private static final DeviceRgb TEAL       = new DeviceRgb(0x0D, 0x94, 0x88);
    private static final DeviceRgb WHITE      = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(0xF8, 0xFA, 0xFC);
    private static final DeviceRgb MID_GRAY   = new DeviceRgb(0xE2, 0xE8, 0xF0);
    private static final DeviceRgb TEXT_GRAY  = new DeviceRgb(0x64, 0x74, 0x8B);
    private static final DeviceRgb TEXT_DARK  = new DeviceRgb(0x0F, 0x17, 0x2A);
    private static final DeviceRgb ORANGE     = new DeviceRgb(0xEA, 0x58, 0x0C);

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm");

    @Transactional(readOnly = true)
    public byte[] generateReceipt(UUID deliveryId, TenantId tenantId) {
        FuelDelivery delivery = deliveryRepository
                .findActiveById(tenantId, deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Delivery", deliveryId.toString()));

        if (!"DELIVERED".equals(delivery.getStatus())) {
            throw new IllegalStateException(
                    "Receipt can only be generated for completed deliveries"
            );
        }

        TenantDetails tenant = tenantFacade
                .findTenantDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant", tenantId.toString()));

        try {
            return buildPdf(delivery, tenant);
        } catch (Exception e) {
            log.error("Receipt PDF generation failed delivery={}: {}",
                    deliveryId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate delivery receipt", e);
        }
    }

    private byte[] buildPdf(FuelDelivery d, TenantDetails tenant) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
        Document doc   = new Document(pdf, PageSize.A5); // A5 — receipt size
        doc.setMargins(30, 40, 40, 40);

        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

        addReceiptHeader(doc, d, tenant, regular, bold);
        addDeliveryDetails(doc, d, regular, bold);
        addMeterReadings(doc, d, regular, bold);
        addProofOfDelivery(doc, d, regular, bold);
        addReceiptFooter(doc, d, tenant, regular);

        doc.close();
        log.info("Generated delivery receipt={} delivery={}",
                d.getReceiptNumber(), d.getId());
        return baos.toByteArray();
    }

    private void addReceiptHeader(Document doc, FuelDelivery d,
                                  TenantDetails tenant,
                                  PdfFont regular, PdfFont bold) {
        // Header bar
        Table header = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        // Left — supplier
        Cell left = new Cell().setBorder(Border.NO_BORDER)
                .setBackgroundColor(NAVY).setPadding(14);
        left.add(new Paragraph(tenant.companyName())
                .setFont(bold).setFontSize(14).setFontColor(WHITE).setMarginBottom(3));
        if (tenant.vatNumber() != null) {
            left.add(new Paragraph("VAT: " + tenant.vatNumber())
                    .setFont(regular).setFontSize(8).setFontColor(LIGHT_GRAY));
        }

        // Right — receipt label + number
        Cell right = new Cell().setBorder(Border.NO_BORDER)
                .setBackgroundColor(ORANGE).setPadding(14)
                .setTextAlignment(TextAlignment.RIGHT);
        right.add(new Paragraph("FUEL DELIVERY RECEIPT")
                .setFont(bold).setFontSize(11).setFontColor(WHITE).setMarginBottom(4));
        right.add(new Paragraph(d.getReceiptNumber() != null
                ? d.getReceiptNumber() : "PENDING")
                .setFont(bold).setFontSize(13).setFontColor(WHITE));

        header.addCell(left);
        header.addCell(right);
        doc.add(header);
        doc.add(new Paragraph().setMarginTop(12));
    }

    private void addDeliveryDetails(Document doc, FuelDelivery d,
                                    PdfFont regular, PdfFont bold) {
        doc.add(sectionLabel("DELIVERY DETAILS", bold));

        Table details = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        // Left column
        Cell left = new Cell().setBorder(Border.NO_BORDER)
                .setBackgroundColor(LIGHT_GRAY).setPadding(10);

        String deliveredAt = d.getDeliveredAt() != null
                ? d.getDeliveredAt().atZone(ZoneId.systemDefault()).format(DT_FMT)
                : "—";
        left.add(metaLine("Date delivered:", deliveredAt, regular, bold));
        left.add(metaLine("Fuel type:", d.getFuelType(), regular, bold));
        left.add(metaLine("Driver:", nvl(d.getDriverName()), regular, bold));
        left.add(metaLine("Vehicle reg:", nvl(d.getVehicleReg()), regular, bold));

        // Right column
        Cell right = new Cell().setBorder(Border.NO_BORDER)
                .setBackgroundColor(LIGHT_GRAY).setPadding(10);

        if (d.getDeliveryAddress() != null) {
            right.add(sectionLabelSmall("Delivery site:", bold));
            right.add(new Paragraph(formatAddress(d.getDeliveryAddress()))
                    .setFont(regular).setFontSize(9).setFontColor(TEXT_DARK));
        }

        details.addCell(left);
        details.addCell(right);
        doc.add(details);
        doc.add(new Paragraph().setMarginTop(10));
    }

    private void addMeterReadings(Document doc, FuelDelivery d,
                                  PdfFont regular, PdfFont bold) {
        doc.add(sectionLabel("FUEL QUANTITY", bold));

        Table qty = new Table(UnitValue.createPercentArray(new float[]{50, 25, 25}))
                .setWidth(UnitValue.createPercentValue(100));

        // Header
        for (String h : new String[]{"Description", "Ordered", "Delivered"}) {
            qty.addHeaderCell(new Cell()
                    .setBackgroundColor(NAVY).setBorder(Border.NO_BORDER).setPadding(7)
                    .add(new Paragraph(h).setFont(bold).setFontSize(9).setFontColor(WHITE)));
        }

        // Litres row
        qty.addCell(dataCell("Litres", LIGHT_GRAY, bold));
        qty.addCell(dataCell(fmtL(d.getLitresOrdered()), LIGHT_GRAY, regular));
        qty.addCell(dataCell(fmtL(d.getLitresDelivered()), LIGHT_GRAY, bold));

        // Price row
        qty.addCell(dataCell("Price per litre", WHITE, regular));
        qty.addCell(dataCell("—", WHITE, regular));
        qty.addCell(dataCell(fmtR(d.getPricePerLitre()), WHITE, regular));

        // Total row
        qty.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setBackgroundColor(NAVY).setPadding(7)
                .add(new Paragraph("TOTAL AMOUNT")
                        .setFont(bold).setFontSize(10).setFontColor(WHITE)));
        qty.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setBackgroundColor(NAVY).setPadding(7)
                .add(new Paragraph("").setFont(bold).setFontColor(WHITE)));
        qty.addCell(new Cell().setBorder(Border.NO_BORDER)
                .setBackgroundColor(NAVY).setPadding(7)
                .setTextAlignment(TextAlignment.RIGHT)
                .add(new Paragraph(fmtR(d.getTotalAmount()))
                        .setFont(bold).setFontSize(12).setFontColor(WHITE)));

        doc.add(qty);

        // Meter readings if available
        if (d.getMeterReadingStart() != null || d.getMeterReadingEnd() != null) {
            doc.add(new Paragraph().setMarginTop(8));
            Table meters = new Table(UnitValue.createPercentArray(new float[]{34, 33, 33}))
                    .setWidth(UnitValue.createPercentValue(100));

            for (String h : new String[]{"Meter check", "Start reading", "End reading"}) {
                meters.addHeaderCell(new Cell()
                        .setBackgroundColor(TEAL).setBorder(Border.NO_BORDER).setPadding(6)
                        .add(new Paragraph(h).setFont(bold).setFontSize(8.5f).setFontColor(WHITE)));
            }

            meters.addCell(dataCell("Pump meter (L)", LIGHT_GRAY, regular));
            meters.addCell(dataCell(
                    d.getMeterReadingStart() != null ? fmtL(d.getMeterReadingStart()) : "—",
                    LIGHT_GRAY, regular));
            meters.addCell(dataCell(
                    d.getMeterReadingEnd() != null ? fmtL(d.getMeterReadingEnd()) : "—",
                    LIGHT_GRAY, bold));

            doc.add(meters);
        }

        doc.add(new Paragraph().setMarginTop(12));
    }

    private void addProofOfDelivery(Document doc, FuelDelivery d,
                                    PdfFont regular, PdfFont bold) {
        doc.add(sectionLabel("PROOF OF DELIVERY", bold));

        Table pod = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        // Delivered by
        Cell deliveredBy = podSection("DELIVERED BY", bold, regular);
        deliveredBy.add(podField("Name:", nvl(d.getDriverName()), regular, bold));
        deliveredBy.add(podField("Vehicle:", nvl(d.getVehicleReg()), regular, bold));
        deliveredBy.add(signatureLine("Signature:", regular));

        // Received by
        Cell receivedBy = podSection("RECEIVED BY", bold, regular);
        receivedBy.add(podField("Name:", nvl(d.getReceiverName()), regular, bold));
        receivedBy.add(podField("ID / Badge:", nvl(d.getReceiverIdBadge()), regular, bold));

// WHY? Highlights on-behalf signing clearly for audit purposes
        if (d.isSignedOnBehalf() && d.getOnBehalfOf() != null) {
            receivedBy.add(new Paragraph()
                    .add(new Text("Signing on behalf of:  ")
                            .setFont(regular).setFontSize(8).setFontColor(TEXT_GRAY))
                    .add(new Text(d.getOnBehalfOf())
                            .setFont(bold).setFontSize(8).setFontColor(ORANGE))
                    .setMarginBottom(4));
        }

        receivedBy.add(signatureLine("Signature:", regular));

        pod.addCell(deliveredBy);
        pod.addCell(receivedBy);
        doc.add(pod);

        // Date/time box
        doc.add(new Paragraph().setMarginTop(8));
        String deliveredAt = d.getDeliveredAt() != null
                ? d.getDeliveredAt().atZone(ZoneId.systemDefault()).format(DT_FMT)
                : "____________________";

        Table dtBox = new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100));
        dtBox.addCell(new Cell()
                .setBackgroundColor(LIGHT_GRAY)
                .setBorder(new SolidBorder(MID_GRAY, 1))
                .setPadding(8)
                .add(new Paragraph()
                        .add(new Text("Date & Time of Delivery:  ").setFont(bold).setFontSize(9))
                        .add(new Text(deliveredAt).setFont(regular).setFontSize(9))));
        doc.add(dtBox);
        doc.add(new Paragraph().setMarginTop(10));
    }

    private void addReceiptFooter(Document doc, FuelDelivery d,
                                  TenantDetails tenant, PdfFont regular) {
        doc.add(new Paragraph()
                .setBorderTop(new SolidBorder(MID_GRAY, 1))
                .setMarginTop(8).setMarginBottom(6));

        doc.add(new Paragraph(
                "This document serves as proof of fuel delivery. " +
                        "Receipt No: " + nvl(d.getReceiptNumber()) +
                        "  |  Generated by HandyFlow Fuel & Logistics"
        ).setFont(regular).setFontSize(7.5f).setFontColor(TEXT_GRAY)
                .setTextAlignment(TextAlignment.CENTER));

        if (tenant.vatNumber() != null) {
            doc.add(new Paragraph("Supplier VAT: " + tenant.vatNumber())
                    .setFont(regular).setFontSize(7.5f).setFontColor(TEXT_GRAY)
                    .setTextAlignment(TextAlignment.CENTER));
        }
    }

    // ── Cell / paragraph helpers ──────────────────────────────────────────────

    private Paragraph sectionLabel(String text, PdfFont bold) {
        return new Paragraph(text)
                .setFont(bold).setFontSize(8).setFontColor(TEXT_GRAY)
                .setCharacterSpacing(0.8f).setMarginBottom(5);
    }

    private Paragraph sectionLabelSmall(String text, PdfFont bold) {
        return new Paragraph(text)
                .setFont(bold).setFontSize(8).setFontColor(TEXT_GRAY).setMarginBottom(3);
    }

    private Paragraph metaLine(String label, String value,
                               PdfFont regular, PdfFont bold) {
        return new Paragraph()
                .add(new Text(label + "  ").setFont(regular).setFontSize(8.5f)
                        .setFontColor(TEXT_GRAY))
                .add(new Text(value).setFont(bold).setFontSize(8.5f)
                        .setFontColor(TEXT_DARK))
                .setMarginBottom(3);
    }

    private Cell dataCell(String text, DeviceRgb bg, PdfFont font) {
        return new Cell()
                .setBackgroundColor(bg).setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(MID_GRAY, 0.5f))
                .setPadding(6)
                .add(new Paragraph(text).setFont(font).setFontSize(9).setFontColor(TEXT_DARK));
    }

    private Cell podSection(String title, PdfFont bold, PdfFont regular) {
        Cell c = new Cell().setBorder(new SolidBorder(MID_GRAY, 1)).setPadding(10);
        c.add(new Paragraph(title).setFont(bold).setFontSize(8)
                .setFontColor(TEXT_GRAY).setCharacterSpacing(0.8f).setMarginBottom(6));
        return c;
    }

    private Paragraph podField(String label, String value,
                               PdfFont regular, PdfFont bold) {
        return new Paragraph()
                .add(new Text(label + "  ").setFont(regular).setFontSize(8.5f)
                        .setFontColor(TEXT_GRAY))
                .add(new Text(value.isEmpty() ? "____________________" : value)
                        .setFont(bold).setFontSize(8.5f).setFontColor(TEXT_DARK))
                .setMarginBottom(5);
    }

    private Paragraph signatureLine(String label, PdfFont regular) {
        return new Paragraph()
                .add(new Text(label + "  ").setFont(regular).setFontSize(8.5f)
                        .setFontColor(TEXT_GRAY))
                .add(new Text("____________________________")
                        .setFont(regular).setFontSize(8.5f).setFontColor(MID_GRAY))
                .setMarginTop(8).setMarginBottom(4);
    }

    private String formatAddress(Map<String, String> address) {
        return String.join(", ",
                nvl(address.get("street")),
                nvl(address.get("suburb")),
                nvl(address.get("city")),
                nvl(address.get("postalCode"))
        ).replaceAll("(, )+", ", ").replaceAll("^, |, $", "");
    }

    private String fmtL(BigDecimal value) {
        if (value == null) return "—";
        return String.format(java.util.Locale.US, "%,.1f L",
                value.setScale(1, RoundingMode.HALF_UP));
    }

    private String fmtR(BigDecimal value) {
        if (value == null) return "—";
        return "R " + String.format(java.util.Locale.US, "%,.2f",
                value.setScale(2, RoundingMode.HALF_UP));
    }

    private String nvl(String value) {
        return value != null && !value.isBlank() ? value : "";
    }
}