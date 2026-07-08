package za.co.handyflow.platform.contracting.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractParty;
import za.co.handyflow.platform.contracting.domain.model.ContractSignature;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * FIX: full migration from iText7 core (com.itextpdf.*) to OpenPDF
 * (com.lowagie.*). iText7-core is AGPL-licensed unless a commercial license
 * has been purchased — confirmed no such license exists for this project.
 * AGPL's network-use clause generally means offering this functionality
 * over a network (which this SaaS inherently does) can trigger an
 * obligation to release the complete source under AGPL terms — a serious
 * consequence for a closed-source commercial product. OpenPDF is LGPL and
 * carries no such obligation; it's the same library already used for every
 * other PDF generator in this codebase (FleetLogbookService,
 * CreativePdfGenerator) — this file was the one outlier still on iText7.
 * <p>
 * Every fix from the previous formatting pass is preserved: proper date
 * formatting (was raw LocalDate.toString()), thousands-separated currency,
 * the visible document verification hash, localhost normalization for the
 * IPv6 loopback address, and correct <ul>/<li> handling in the contract
 * body so bullet lists don't run together into one sentence.
 * <p>
 * ONE DELIBERATE SIMPLIFICATION vs. the iText7 version: the page footer now
 * reads "Page X" instead of "Page X of Y". Getting the total page count
 * requires OpenPDF's PdfTemplate placeholder trick (write a blank
 * reservation on each page, fill in the real total once the document
 * closes and the page count is finally known) combined with precise
 * text-width measurement to position it correctly after variable-length
 * text. That's exactly the kind of subtle, easy-to-get-wrong detail that's
 * hard to verify without actually rendering and inspecting the output — not
 * something to ship unverified in a licensing-driven rewrite. If the total
 * page count matters enough to be worth the added complexity, it's a
 * contained follow-up, not something folded in here.
 */
@Slf4j
@Component
public class ContractPdfGenerator {

    private static final Color BRAND_DARK  = new Color(27, 58, 107);
    private static final Color BRAND_TEAL  = new Color(13, 148, 136);
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);
    private static final Color MID_GRAY    = new Color(100, 116, 139);
    private static final Color SUCCESS     = new Color(22, 101, 52);
    private static final Color SUCCESS_BG  = new Color(220, 252, 231);
    private static final Color PENDING_CLR = new Color(180, 117, 23);

    private static final Font BRAND_FONT    = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_DARK);
    private static final Font TENANT_FONT   = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font VAT_FONT      = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font TYPE_FONT     = new Font(Font.HELVETICA, 11, Font.BOLD, BRAND_TEAL);
    private static final Font NUMBER_FONT   = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font BADGE_FONT    = new Font(Font.HELVETICA, 10, Font.BOLD, SUCCESS);
    private static final Font TITLE_FONT    = new Font(Font.HELVETICA, 18, Font.BOLD, BRAND_DARK);
    private static final Font SECTION_FONT  = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_DARK);
    private static final Font LABEL_FONT    = new Font(Font.HELVETICA, 9, Font.BOLD, MID_GRAY);
    private static final Font VALUE_FONT    = new Font(Font.HELVETICA, 11, Font.NORMAL, BRAND_DARK);
    private static final Font BODY_FONT     = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font ROLE_FONT     = new Font(Font.HELVETICA, 9, Font.BOLD, MID_GRAY);
    private static final Font NAME_FONT     = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_DARK);
    private static final Font COMPANY_FONT  = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font EMAIL_FONT    = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font SIGNED_FONT   = new Font(Font.HELVETICA, 11, Font.BOLD, SUCCESS);
    private static final Font PENDING_FONT  = new Font(Font.HELVETICA, 11, Font.BOLD, PENDING_CLR);
    private static final Font META_SMALL    = new Font(Font.HELVETICA, 8, Font.NORMAL, MID_GRAY);
    private static final Font SIG_LINE_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font AUDIT_INTRO   = new Font(Font.HELVETICA, 9, Font.NORMAL, MID_GRAY);
    private static final Font AUDIT_HEADER  = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font AUDIT_CELL    = new Font(Font.HELVETICA, 9, Font.NORMAL, BRAND_DARK);
    private static final Font HASH_FONT     = new Font(Font.HELVETICA, 7, Font.NORMAL, MID_GRAY);
    private static final Font LEGAL_FONT    = new Font(Font.HELVETICA, 8, Font.ITALIC, MID_GRAY);
    private static final Font FOOTER_FONT   = new Font(Font.HELVETICA, 8, Font.NORMAL, MID_GRAY);

    private static final DateTimeFormatter DT = DateTimeFormatter
            .ofPattern("dd MMM yyyy HH:mm 'SAST'")
            .withZone(ZoneId.of("Africa/Johannesburg"));
    private static final DateTimeFormatter D = DateTimeFormatter
            .ofPattern("dd MMM yyyy")
            .withZone(ZoneId.of("Africa/Johannesburg"));

    public byte[] generate(Contract contract, List<ContractSignature> signatures,
                           String tenantName, String tenantVat) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 70);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterHandler(contract.getContractNumber()));

            doc.open();

            addHeader(doc, contract, tenantName, tenantVat);
            addDivider(doc, BRAND_DARK);
            addContractMeta(doc, contract);
            addDivider(doc, LIGHT_GRAY);
            addBody(doc, contract.getBody());
            addDivider(doc, BRAND_DARK);
            addPartiesSection(doc, contract.getParties(), signatures);
            addAuditTrail(doc, signatures, contract.getParties());
            addLegalFooter(doc, contract);

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
                           String tenantName, String tenantVat) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1, 1});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);
        left.addElement(new Paragraph("HandyFlow", BRAND_FONT));
        Paragraph tenantP = new Paragraph(tenantName, TENANT_FONT);
        tenantP.setSpacingBefore(2);
        left.addElement(tenantP);
        if (tenantVat != null && !tenantVat.isBlank()) {
            left.addElement(new Paragraph("VAT: " + tenantVat, VAT_FONT));
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(0);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph typeP = new Paragraph(contract.getContractType().replace("_", " "), TYPE_FONT);
        typeP.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(typeP);

        Paragraph numP = new Paragraph(contract.getContractNumber(), NUMBER_FONT);
        numP.setAlignment(Element.ALIGN_RIGHT);
        numP.setSpacingBefore(2);
        right.addElement(numP);

        PdfPTable badgeTable = new PdfPTable(1);
        badgeTable.setWidthPercentage(35);
        badgeTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell badge = new PdfPCell(new Phrase("SIGNED", BADGE_FONT));
        badge.setBackgroundColor(SUCCESS_BG);
        badge.setBorder(Rectangle.NO_BORDER);
        badge.setPadding(4);
        badge.setHorizontalAlignment(Element.ALIGN_CENTER);
        badgeTable.addCell(badge);
        right.addElement(badgeTable);

        header.addCell(right);
        doc.add(header);

        Paragraph title = new Paragraph(contract.getTitle(), TITLE_FONT);
        title.setSpacingBefore(16);
        title.setSpacingAfter(4);
        doc.add(title);
    }

    // ── Contract meta ─────────────────────────────────────────────────────────

    private void addContractMeta(Document doc, Contract contract) throws DocumentException {
        PdfPTable meta = new PdfPTable(3);
        meta.setWidthPercentage(100);
        meta.setSpacingBefore(8);

        // FIX: D (a proper "dd MMM yyyy" formatter) previously sat unused —
        // start/end date fell through to LocalDate's raw toString()
        // ("2026-08-01"), inconsistent with signedAt's nicely formatted
        // "08 Jul 2026 15:35 SAST" on the same document.
        addMetaCell(meta, "Start date",
                contract.getStartDate() != null ? D.format(contract.getStartDate()) : "—");
        addMetaCell(meta, "End date",
                contract.getEndDate() != null ? D.format(contract.getEndDate()) : "—");
        addMetaCell(meta, "Contract value",
                contract.getValueAmount() != null
                        ? "R " + String.format("%,.2f", contract.getValueAmount()) : "—");

        if (contract.getSignedAt() != null)
            addMetaCell(meta, "Signed on", DT.format(contract.getSignedAt()));
        if (contract.getNotes() != null && !contract.getNotes().isBlank())
            addMetaCell(meta, "Notes", contract.getNotes());

        doc.add(meta);
    }

    private void addMetaCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(LIGHT_GRAY);
        cell.setPadding(8);
        cell.addElement(new Paragraph(label, LABEL_FONT));
        Paragraph valueP = new Paragraph(value, VALUE_FONT);
        valueP.setSpacingBefore(2);
        cell.addElement(valueP);
        table.addCell(cell);
    }

    // ── Contract body ─────────────────────────────────────────────────────────

    private void addBody(Document doc, String htmlBody) throws DocumentException {
        Paragraph heading = new Paragraph("Contract terms", SECTION_FONT);
        heading.setSpacingBefore(16);
        doc.add(heading);

        // FIX: <ul>/<li> were previously falling through to the catch-all
        // tag-stripper with no line break inserted at all — every bullet in
        // a list rendered as one unbroken run-on sentence. This surfaced
        // the moment the system templates were upgraded to use proper
        // <ul>/<li> for sub-clauses (Obligations, Exclusions, etc.). <li>
        // now maps to its own paragraph block (double newline + bullet
        // marker) via the same \n\n-split mechanism <p> and <h3> already
        // use — deliberately not relying on a single embedded \n rendering
        // correctly inside one Paragraph, which isn't guaranteed.
        String plain = htmlBody
                .replaceAll("<h[1-6][^>]*>", "\n")
                .replaceAll("</h[1-6]>", "\n")
                .replaceAll("<p[^>]*>", "\n")
                .replaceAll("</p>", "\n")
                .replaceAll("<br[^>]*>", "\n")
                .replaceAll("<ul[^>]*>", "\n")
                .replaceAll("</ul>", "\n")
                .replaceAll("<li[^>]*>", "\n\n\u2022 ")
                .replaceAll("</li>", "")
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
                Paragraph p = new Paragraph(trimmed, BODY_FONT);
                p.setSpacingAfter(6);
                p.setMultipliedLeading(1.4f);
                doc.add(p);
            }
        }
    }

    // ── Parties section ───────────────────────────────────────────────────────

    private void addPartiesSection(Document doc, List<ContractParty> parties,
                                   List<ContractSignature> signatures) throws DocumentException {
        Paragraph heading = new Paragraph("Signatures", SECTION_FONT);
        heading.setSpacingBefore(16);
        doc.add(heading);

        for (ContractParty party : parties) {
            ContractSignature sig = signatures.stream()
                    .filter(s -> s.getPartyId().equals(party.getId()))
                    .findFirst().orElse(null);

            PdfPTable partyBox = new PdfPTable(2);
            partyBox.setWidthPercentage(100);
            partyBox.setWidths(new float[]{2, 1});
            partyBox.setSpacingBefore(10);
            partyBox.setSpacingAfter(4);

            // Left: party details
            PdfPCell left = new PdfPCell();
            left.setBorder(Rectangle.NO_BORDER);
            left.setBackgroundColor(LIGHT_GRAY);
            left.setPadding(12);
            left.addElement(new Paragraph(party.getPartyRole(), ROLE_FONT));
            left.addElement(new Paragraph(party.getFullName(), NAME_FONT));
            if (party.getCompanyName() != null)
                left.addElement(new Paragraph(party.getCompanyName(), COMPANY_FONT));
            if (party.getEmail() != null)
                left.addElement(new Paragraph(party.getEmail(), EMAIL_FONT));
            partyBox.addCell(left);

            // Right: signing status
            PdfPCell right = new PdfPCell();
            right.setBorder(Rectangle.NO_BORDER);
            right.setBackgroundColor(LIGHT_GRAY);
            right.setPadding(12);
            right.setHorizontalAlignment(Element.ALIGN_RIGHT);

            if ("SIGNED".equals(party.getSigningStatus()) && sig != null) {
                Paragraph signedP = new Paragraph("SIGNED", SIGNED_FONT);
                signedP.setAlignment(Element.ALIGN_RIGHT);
                right.addElement(signedP);

                Paragraph dateP = new Paragraph(DT.format(party.getSignedAt()), META_SMALL);
                dateP.setAlignment(Element.ALIGN_RIGHT);
                dateP.setSpacingBefore(4);
                right.addElement(dateP);

                Paragraph otpP = new Paragraph("OTP verified \u00b7 " + party.getPartyType(), META_SMALL);
                otpP.setAlignment(Element.ALIGN_RIGHT);
                right.addElement(otpP);

                if (sig.getPhoneLast4() != null) {
                    Paragraph phoneP = new Paragraph("Phone: ****" + sig.getPhoneLast4(), META_SMALL);
                    phoneP.setAlignment(Element.ALIGN_RIGHT);
                    right.addElement(phoneP);
                }
            } else {
                Paragraph pendingP = new Paragraph("PENDING", PENDING_FONT);
                pendingP.setAlignment(Element.ALIGN_RIGHT);
                right.addElement(pendingP);
            }
            partyBox.addCell(right);
            doc.add(partyBox);

            // Signature line
            Paragraph sigLine = new Paragraph(
                    "_________________________________    " + party.getFullName(), SIG_LINE_FONT);
            sigLine.setSpacingBefore(2);
            sigLine.setIndentationLeft(12);
            doc.add(sigLine);
        }
    }

    // ── Audit trail ───────────────────────────────────────────────────────────

    private void addAuditTrail(Document doc, List<ContractSignature> signatures,
                               List<ContractParty> parties) throws DocumentException {
        if (signatures.isEmpty()) return;

        Paragraph heading = new Paragraph("Audit trail", SECTION_FONT);
        heading.setSpacingBefore(20);
        doc.add(heading);

        Paragraph intro = new Paragraph(
                "The following events were recorded during the signing of this contract. " +
                        "This audit trail is generated by HandyFlow and is tamper-evident.", AUDIT_INTRO);
        intro.setSpacingAfter(8);
        doc.add(intro);

        PdfPTable audit = new PdfPTable(5);
        audit.setWidthPercentage(100);
        audit.setWidths(new float[]{2, 2, 2, 1.5f, 2});

        for (String h : new String[]{"Party", "Company", "Signed at (SAST)", "Phone", "IP address"}) {
            PdfPCell headerCell = new PdfPCell(new Phrase(h, AUDIT_HEADER));
            headerCell.setBackgroundColor(BRAND_DARK);
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerCell.setPadding(6);
            audit.addCell(headerCell);
        }

        for (ContractSignature sig : signatures) {
            ContractParty party = parties.stream()
                    .filter(p -> p.getId().equals(sig.getPartyId()))
                    .findFirst().orElse(null);

            String partyName   = party != null ? party.getFullName() : "Unknown";
            String companyName = party != null && party.getCompanyName() != null
                    ? party.getCompanyName() : "—";

            for (String val : new String[]{
                    partyName,
                    companyName,
                    DT.format(sig.getSignedAt()),
                    sig.getPhoneLast4() != null ? "****" + sig.getPhoneLast4() : "—",
                    formatIp(sig.getIpAddress())
            }) {
                PdfPCell cell = new PdfPCell(new Phrase(val, AUDIT_CELL));
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setBackgroundColor(LIGHT_GRAY);
                cell.setPadding(6);
                audit.addCell(cell);
            }
        }
        doc.add(audit);
    }

    // ── Legal footer ──────────────────────────────────────────────────────────

    private void addLegalFooter(Document doc, Contract contract) throws DocumentException {
        // Document.bodyHash was already computed and stored (see
        // SigningTokenService.sha256() — used for tamper detection when a
        // party signs) but never actually appeared anywhere on the PDF
        // itself until this fix — a recipient had no way to verify their
        // copy matched HandyFlow's records without logging into the app.
        if (contract.getBodyHash() != null && !contract.getBodyHash().isBlank()) {
            Paragraph hashP = new Paragraph(
                    "Document verification hash (SHA-256): " + contract.getBodyHash(), HASH_FONT);
            hashP.setSpacingBefore(10);
            doc.add(hashP);
        }

        Paragraph legal = new Paragraph(
                "This contract was electronically signed via HandyFlow (handyflow.co.za). " +
                        "Electronic signatures on this document are valid and legally binding in terms of " +
                        "Section 13 of the Electronic Communications and Transactions Act 25 of 2002 (ECTA). " +
                        "The audit trail above constitutes sufficient evidence of identity and intention to sign.",
                LEGAL_FONT);
        legal.setSpacingBefore(20);
        doc.add(legal);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Tomcat sometimes resolves localhost to the fully-expanded IPv6
    // loopback form "0:0:0:0:0:0:0:1" rather than the familiar "::1" —
    // technically correct, but confusing to read on a legal document. Only
    // normalizes the two loopback spellings; any real IP is shown exactly
    // as recorded, since that's the actual evidentiary value of this field.
    private String formatIp(String ip) {
        if (ip == null) return "—";
        if (ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) return "localhost";
        return ip;
    }

    private void addDivider(Document doc, Color color) throws DocumentException {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingBefore(8);
        line.setSpacingAfter(8);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(1);
        cell.setBackgroundColor(color);
        cell.setBorder(Rectangle.NO_BORDER);
        line.addCell(cell);
        doc.add(line);
    }

    // ── Page footer handler ───────────────────────────────────────────────────

    private static class FooterHandler extends PdfPageEventHelper {
        private final String contractNumber;
        FooterHandler(String contractNumber) { this.contractNumber = contractNumber; }

        // FIX: was calling cb.beginText()/cb.setFontAndSize()/cb.showText()/
        // cb.endText() directly — a manually-paired begin/end that, on any
        // contract long enough to span more than one page, left the
        // PdfContentByte's internal text-object state unbalanced by the
        // time the next page's initPage()/resetContent() ran its sanity
        // check, throwing IllegalPdfSyntaxException("Unbalanced begin/end
        // text operators") and failing PDF generation entirely — confirmed
        // via a real two-page contract (CTR-2026-00007). ColumnText.
        // showTextAligned(...) is the standard, well-documented OpenPDF
        // pattern for exactly this footer use case: it manages its own
        // beginText/endText pairing internally and safely, rather than
        // hand-rolling it.
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                Phrase footer = new Phrase(
                        "HandyFlow \u00b7 " + contractNumber +
                                " \u00b7 ECTA compliant \u00b7 Page " + writer.getPageNumber(),
                        FOOTER_FONT);
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, footer,
                        document.leftMargin(), document.bottomMargin() - 20, 0);
            } catch (Exception ignored) {
                // A broken footer must never take down PDF generation for a
                // document that otherwise generated correctly.
            }
        }
    }
}