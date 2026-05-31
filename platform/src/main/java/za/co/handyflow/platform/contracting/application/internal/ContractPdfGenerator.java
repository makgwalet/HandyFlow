package za.co.handyflow.platform.contracting.application.internal;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractParty;
import za.co.handyflow.platform.contracting.domain.model.ContractSignature;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class ContractPdfGenerator {

    private static final DeviceRgb BRAND_DARK  = new DeviceRgb(27, 58, 107);
    private static final DeviceRgb BRAND_TEAL  = new DeviceRgb(13, 148, 136);
    private static final DeviceRgb LIGHT_GRAY  = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb MID_GRAY    = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb SUCCESS     = new DeviceRgb(22, 101, 52);
    private static final DeviceRgb SUCCESS_BG  = new DeviceRgb(220, 252, 231);
    private static final DateTimeFormatter DT  = DateTimeFormatter
            .ofPattern("dd MMM yyyy HH:mm 'SAST'")
            .withZone(ZoneId.of("Africa/Johannesburg"));
    private static final DateTimeFormatter D   = DateTimeFormatter
            .ofPattern("dd MMM yyyy")
            .withZone(ZoneId.of("Africa/Johannesburg"));

    public byte[] generate(Contract contract, List<ContractSignature> signatures,
                           String tenantName, String tenantVat) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf  = new PdfDocument(writer);
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE,
                    new FooterHandler(contract.getContractNumber()));

            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(60, 50, 70, 50);

            addHeader(doc, contract, tenantName, tenantVat);
            addDivider(doc, BRAND_DARK);
            addContractMeta(doc, contract);
            addDivider(doc, LIGHT_GRAY);
            addBody(doc, contract.getBody());
            addDivider(doc, BRAND_DARK);
            addPartiesSection(doc, contract.getParties(), signatures);
            addAuditTrail(doc, signatures, contract.getParties());
            addLegalFooter(doc);

            doc.close();
            log.info("Generated PDF for contract={}", contract.getContractNumber());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("PDF generation failed for contract={}: {}", contract.getContractNumber(), e.getMessage());
            throw new RuntimeException("Failed to generate contract PDF", e);
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void addHeader(Document doc, Contract contract,
                           String tenantName, String tenantVat) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100));

        // Left: HandyFlow + tenant name
        Cell left = new Cell().setBorder(null).setPadding(0);
        left.add(new Paragraph("HandyFlow")
                .setFontColor(BRAND_DARK).setFontSize(20).setBold());
        left.add(new Paragraph(tenantName)
                .setFontColor(MID_GRAY).setFontSize(10).setMarginTop(2));
        if (tenantVat != null && !tenantVat.isBlank())
            left.add(new Paragraph("VAT: " + tenantVat)
                    .setFontColor(MID_GRAY).setFontSize(9));
        header.addCell(left);

        // Right: contract type badge + number
        Cell right = new Cell().setBorder(null).setPadding(0)
                .setTextAlignment(TextAlignment.RIGHT);
        right.add(new Paragraph(contract.getContractType().replace("_", " "))
                .setFontColor(BRAND_TEAL).setFontSize(11).setBold());
        right.add(new Paragraph(contract.getContractNumber())
                .setFontColor(MID_GRAY).setFontSize(10).setMarginTop(2));
        right.add(new Paragraph("SIGNED")
                .setFontColor(SUCCESS).setFontSize(10).setBold()
                .setBackgroundColor(SUCCESS_BG).setPadding(3));
        header.addCell(right);

        doc.add(header);

        doc.add(new Paragraph(contract.getTitle())
                .setFontSize(18).setBold().setFontColor(BRAND_DARK)
                .setMarginTop(16).setMarginBottom(4));
    }

    // ── Contract meta ─────────────────────────────────────────────────────────

    private void addContractMeta(Document doc, Contract contract) {
        Table meta = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginTop(8);

        addMetaCell(meta, "Start date",
                contract.getStartDate() != null ? contract.getStartDate().toString() : "—");
        addMetaCell(meta, "End date",
                contract.getEndDate() != null ? contract.getEndDate().toString() : "—");
        addMetaCell(meta, "Contract value",
                contract.getValueAmount() != null
                        ? "R " + contract.getValueAmount().toPlainString() : "—");

        if (contract.getSignedAt() != null)
            addMetaCell(meta, "Signed on", DT.format(contract.getSignedAt()));
        if (contract.getNotes() != null && !contract.getNotes().isBlank())
            addMetaCell(meta, "Notes", contract.getNotes());

        doc.add(meta);
    }

    private void addMetaCell(Table table, String label, String value) {
        Cell cell = new Cell().setBorder(null)
                .setBackgroundColor(LIGHT_GRAY).setPadding(8).setBorderRadius(
                        new com.itextpdf.layout.properties.BorderRadius(4));
        cell.add(new Paragraph(label).setFontSize(9).setFontColor(MID_GRAY).setBold());
        cell.add(new Paragraph(value).setFontSize(11).setFontColor(BRAND_DARK));
        table.addCell(cell);
    }

    // ── Contract body ─────────────────────────────────────────────────────────

    private void addBody(Document doc, String htmlBody) {
        doc.add(new Paragraph("Contract terms")
                .setFontSize(13).setBold().setFontColor(BRAND_DARK).setMarginTop(16));

        // Strip HTML tags and render as plain paragraphs
        String plain = htmlBody
                .replaceAll("<h[1-6][^>]*>", "\n")
                .replaceAll("</h[1-6]>", "\n")
                .replaceAll("<p[^>]*>", "\n")
                .replaceAll("</p>", "\n")
                .replaceAll("<br[^>]*>", "\n")
                .replaceAll("<strong>", "")
                .replaceAll("</strong>", "")
                .replaceAll("<[^>]*>", "")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\n{3,}", "\n\n")
                .trim();

        for (String para : plain.split("\n\n")) {
            String trimmed = para.trim();
            if (!trimmed.isBlank()) {
                doc.add(new Paragraph(trimmed)
                        .setFontSize(10)
                        .setFontColor(ColorConstants.BLACK)
                        .setMarginBottom(6)
                        .setMultipliedLeading(1.4f));
            }
        }
    }

    // ── Parties section ───────────────────────────────────────────────────────

    private void addPartiesSection(Document doc, List<ContractParty> parties,
                                   List<ContractSignature> signatures) {
        doc.add(new Paragraph("Signatures")
                .setFontSize(13).setBold().setFontColor(BRAND_DARK).setMarginTop(16));

        for (ContractParty party : parties) {
            ContractSignature sig = signatures.stream()
                    .filter(s -> s.getPartyId().equals(party.getId()))
                    .findFirst().orElse(null);

            Table partyBox = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginTop(10).setMarginBottom(4);

            // Left: party details
            Cell left = new Cell().setBorder(null)
                    .setBackgroundColor(LIGHT_GRAY).setPadding(12);
            left.add(new Paragraph(party.getPartyRole())
                    .setFontSize(9).setFontColor(MID_GRAY).setBold());
            left.add(new Paragraph(party.getFullName())
                    .setFontSize(13).setBold().setFontColor(BRAND_DARK));
            if (party.getCompanyName() != null)
                left.add(new Paragraph(party.getCompanyName())
                        .setFontSize(10).setFontColor(MID_GRAY));
            if (party.getEmail() != null)
                left.add(new Paragraph(party.getEmail())
                        .setFontSize(9).setFontColor(MID_GRAY));
            partyBox.addCell(left);

            // Right: signing status
            Cell right = new Cell().setBorder(null)
                    .setBackgroundColor(LIGHT_GRAY).setPadding(12)
                    .setTextAlignment(TextAlignment.RIGHT);

            if ("SIGNED".equals(party.getSigningStatus()) && sig != null) {
                right.add(new Paragraph("SIGNED")
                        .setFontSize(11).setBold().setFontColor(SUCCESS));
                right.add(new Paragraph(DT.format(party.getSignedAt()))
                        .setFontSize(8).setFontColor(MID_GRAY).setMarginTop(4));
                right.add(new Paragraph("OTP verified · " + party.getPartyType())
                        .setFontSize(8).setFontColor(MID_GRAY));
                if (sig.getPhoneLast4() != null)
                    right.add(new Paragraph("Phone: ****" + sig.getPhoneLast4())
                            .setFontSize(8).setFontColor(MID_GRAY));
            } else {
                right.add(new Paragraph("PENDING")
                        .setFontSize(11).setBold()
                        .setFontColor(new DeviceRgb(180, 117, 23)));
            }
            partyBox.addCell(right);
            doc.add(partyBox);

            // Signature line
            doc.add(new Paragraph("_________________________________    " + party.getFullName())
                    .setFontSize(9).setFontColor(MID_GRAY).setMarginTop(2).setMarginLeft(12));
        }
    }

    // ── Audit trail ───────────────────────────────────────────────────────────

    private void addAuditTrail(Document doc, List<ContractSignature> signatures,
                               List<ContractParty> parties) {
        if (signatures.isEmpty()) return;

        doc.add(new Paragraph("Audit trail")
                .setFontSize(13).setBold().setFontColor(BRAND_DARK).setMarginTop(20));
        doc.add(new Paragraph(
                "The following events were recorded during the signing of this contract. " +
                        "This audit trail is generated by HandyFlow and is tamper-evident.")
                .setFontSize(9).setFontColor(MID_GRAY).setMarginBottom(8));

        Table audit = new Table(
                UnitValue.createPercentArray(new float[]{2, 2, 2, 1.5f, 2}))
                .setWidth(UnitValue.createPercentValue(100));

        for (String h : new String[]{"Party", "Company", "Signed at (SAST)",
                "Phone", "IP address"}) {
            audit.addHeaderCell(new Cell()
                    .setBackgroundColor(BRAND_DARK).setBorder(null).setPadding(6)
                    .add(new Paragraph(h).setFontColor(ColorConstants.WHITE)
                            .setFontSize(9).setBold()));
        }

        for (ContractSignature sig : signatures) {
            ContractParty party = parties.stream()
                    .filter(p -> p.getId().equals(sig.getPartyId()))
                    .findFirst().orElse(null);

            String partyName    = party != null ? party.getFullName()    : "Unknown";
            String companyName  = party != null && party.getCompanyName() != null
                    ? party.getCompanyName() : "—";

            for (String val : new String[]{
                    partyName,
                    companyName,
                    DT.format(sig.getSignedAt()),
                    sig.getPhoneLast4() != null ? "****" + sig.getPhoneLast4() : "—",
                    sig.getIpAddress()  != null ? sig.getIpAddress() : "—"
            }) {
                audit.addCell(new Cell().setBorder(null)
                        .setBackgroundColor(LIGHT_GRAY).setPadding(6)
                        .add(new Paragraph(val).setFontSize(9)
                                .setFontColor(BRAND_DARK)));
            }
        }
        doc.add(audit);
    }

    // ── Legal footer ──────────────────────────────────────────────────────────

    private void addLegalFooter(Document doc) {
        doc.add(new Paragraph(
                "This contract was electronically signed via HandyFlow (handyflow.co.za). " +
                        "Electronic signatures on this document are valid and legally binding in terms of " +
                        "Section 13 of the Electronic Communications and Transactions Act 25 of 2002 (ECTA). " +
                        "The audit trail above constitutes sufficient evidence of identity and intention to sign.")
                .setFontSize(8).setFontColor(MID_GRAY)
                .setMarginTop(20).setItalic()
                .setBorderTop(new com.itextpdf.layout.borders.SolidBorder(LIGHT_GRAY, 1))
                .setPaddingTop(8));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void addDivider(Document doc, DeviceRgb color) {
        doc.add(new Paragraph()
                .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(color, 1))
                .setMarginTop(8).setMarginBottom(8));
    }

    // ── Page footer handler ───────────────────────────────────────────────────

    private static class FooterHandler implements IEventHandler {
        private final String contractNumber;
        FooterHandler(String contractNumber) { this.contractNumber = contractNumber; }

        @Override
        public void handleEvent(Event event) {
            try {
                PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
                PdfDocument pdfDoc = docEvent.getDocument();
                PdfPage page       = docEvent.getPage();
                int pageNum        = pdfDoc.getPageNumber(page);
                int totalPages     = pdfDoc.getNumberOfPages();

                Rectangle pageSize = page.getPageSize();
                PdfCanvas canvas   = new PdfCanvas(page);
                canvas.beginText()
                        .setFontAndSize(
                                pdfDoc.getDefaultFont(), 8)
                        .moveText(50, 30)
                        .showText("HandyFlow · " + contractNumber +
                                " · ECTA compliant · Page " + pageNum + " of " + totalPages)
                        .endText();
                canvas.release();
            } catch (Exception ignored) {}
        }
    }
}
