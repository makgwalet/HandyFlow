package za.co.handyflow.platform.contracting.application.internal;

import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractParty;
import za.co.handyflow.platform.contracting.domain.model.ContractSignature;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

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
 * The footer reads "Page X of Y" (total filled in when the document closes).
 */
@Slf4j
@Component
public class ContractPdfGenerator {

    private final TenantFacade tenants;

    public ContractPdfGenerator(TenantFacade tenants) { this.tenants = tenants; }

    /** Renders the contract with the tenant's own company name and VAT number in the header. */
    public byte[] generate(Contract contract, List<ContractSignature> signatures, TenantId tenantId) {
        TenantDetails t = tenants.findTenantDetails(tenantId).orElse(null);
        String name = t != null && t.companyName() != null && !t.companyName().isBlank() ? t.companyName() : "HandyFlow";
        String vat = t != null && t.vatNumber() != null && !t.vatNumber().isBlank() ? t.vatNumber() : null;
        return generate(contract, signatures, name, vat);
    }

    private static final Color BRAND_DARK  = new Color(27, 58, 107);
    private static final Color BRAND_TEAL  = new Color(13, 148, 136);
    private static final Color LIGHT_GRAY  = new Color(248, 250, 252);
    private static final Color MID_GRAY    = new Color(100, 116, 139);
    private static final Color SUCCESS     = new Color(22, 101, 52);
    private static final Color SUCCESS_BG  = new Color(220, 252, 231);
    private static final Color PENDING_CLR = new Color(180, 117, 23);
    private static final Color PENDING_BG  = new Color(254, 243, 199);
    private static final Color NEUTRAL_BG  = new Color(226, 232, 240);
    private static final Color DANGER      = new Color(153, 27, 27);
    private static final Color DANGER_BG   = new Color(254, 226, 226);

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
    private static final Font PARTIES_FONT  = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_GRAY);
    private static final Font MONO_FONT     = new Font(Font.COURIER, 9, Font.NORMAL, BRAND_DARK);

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
            writer.setPageEvent(new FooterHandler(contract.getContractNumber(), watermarkFor(contract.getStatus())));

            doc.open();

            addHeader(doc, contract, tenantName, tenantVat);
            addDivider(doc, BRAND_DARK);
            addContractMeta(doc, contract);
            addDivider(doc, LIGHT_GRAY);
            addBody(doc, contract.getBody());
            addDivider(doc, BRAND_DARK);
            addPartiesSection(doc, contract.getParties(), signatures);
            addFingerprint(doc, contract);
            addCertificate(doc, contract, signatures);

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

        Look look = statusLook(contract.getStatus());
        PdfPTable badgeTable = new PdfPTable(1);
        badgeTable.setWidthPercentage(60);
        badgeTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell badge = new PdfPCell(new Phrase(look.label(), new Font(Font.HELVETICA, 9, Font.BOLD, look.fg())));
        badge.setBackgroundColor(look.bg());
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

        String between = partiesLine(contract.getParties());
        if (!between.isBlank()) {
            Paragraph parties = new Paragraph(between, PARTIES_FONT);
            parties.setSpacingAfter(2);
            doc.add(parties);
        }
    }

    private record Look(String label, Color fg, Color bg) {}

    /** Label and colours for the status badge in the header. */
    private static Look statusLook(String status) {
        String s = status == null ? "" : status;
        return switch (s) {
            case "SIGNED" -> new Look("SIGNED", SUCCESS, SUCCESS_BG);
            case "SENT" -> new Look("AWAITING SIGNATURES", PENDING_CLR, PENDING_BG);
            case "UNDER_REVIEW" -> new Look("UNDER REVIEW", PENDING_CLR, PENDING_BG);
            case "TERMINATED" -> new Look("TERMINATED", DANGER, DANGER_BG);
            case "WITHDRAWN" -> new Look("WITHDRAWN - NOT EXECUTED", DANGER, DANGER_BG);
            case "EXPIRED", "ARCHIVED" -> new Look(s, MID_GRAY, NEUTRAL_BG);
            default -> new Look(s.isEmpty() ? "DRAFT" : s.replace('_', ' '), MID_GRAY, NEUTRAL_BG);
        };
    }

    /** Drafts and contracts under review are stamped so a copy cannot be mistaken for the agreed document. */
    private static String watermarkFor(String status) {
        return "DRAFT".equals(status) || "UNDER_REVIEW".equals(status) ? "DRAFT" : null;
    }

    private static String nice(String s) {
        if (s == null || s.isBlank()) return "";
        String t = s.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static String partiesLine(List<ContractParty> parties) {
        if (parties == null || parties.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Between ");
        for (int i = 0; i < parties.size(); i++) {
            ContractParty p = parties.get(i);
            if (i > 0) sb.append(i == parties.size() - 1 ? " and " : ", ");
            String name = p.getCompanyName() != null && !p.getCompanyName().isBlank() ? p.getCompanyName() : p.getFullName();
            sb.append(name == null ? "?" : name);
            if (p.getPartyRole() != null && !p.getPartyRole().isBlank()) sb.append(" (").append(nice(p.getPartyRole())).append(')');
        }
        return sb.toString();
    }

    private static String money(Contract c) {
        if (c.getValueAmount() == null) return "\u2014";
        String cur = c.getCurrency() == null || c.getCurrency().isBlank() || "ZAR".equals(c.getCurrency()) ? "R" : c.getCurrency();
        return cur + " " + String.format(Locale.ENGLISH, "%,.2f", c.getValueAmount());
    }

    private static String methodLabel(String m) {
        if (m == null) return null;
        return switch (m) {
            case "SIMPLE_ELECTRONIC" -> "Simple electronic signature";
            case "ADVANCED_ELECTRONIC" -> "Advanced electronic signature";
            case "WET_INK" -> "Wet-ink signature";
            case "IN_PERSON" -> "In-person signing";
            default -> nice(m);
        };
    }

    private static String term(Contract c) {
        if (c.getStartDate() == null || c.getEndDate() == null || c.getEndDate().isBefore(c.getStartDate())) return "\u2014";
        java.time.Period p = java.time.Period.between(c.getStartDate(), c.getEndDate().plusDays(1));
        StringBuilder sb = new StringBuilder();
        if (p.getYears() > 0) sb.append(p.getYears()).append(p.getYears() == 1 ? " year" : " years");
        if (p.getMonths() > 0) sb.append(sb.length() > 0 ? ", " : "").append(p.getMonths()).append(p.getMonths() == 1 ? " month" : " months");
        if (p.getDays() > 0 || sb.length() == 0) sb.append(sb.length() > 0 ? ", " : "").append(p.getDays()).append(p.getDays() == 1 ? " day" : " days");
        return sb.toString();
    }

    // ── Contract meta ─────────────────────────────────────────────────────────

    private void addContractMeta(Document doc, Contract contract) throws DocumentException {
        PdfPTable meta = new PdfPTable(3);
        meta.setWidthPercentage(100);
        meta.setSpacingBefore(8);

        addMetaCell(meta, "Contract value", money(contract));
        addMetaCell(meta, "Start date", contract.getStartDate() != null ? D.format(contract.getStartDate()) : "\u2014");
        addMetaCell(meta, "End date", contract.getEndDate() != null ? D.format(contract.getEndDate()) : "\u2014");
        addMetaCell(meta, "Term", term(contract));
        addMetaCell(meta, "Signed on", contract.getSignedAt() != null ? DT.format(contract.getSignedAt()) : "Not yet signed");
        String method = methodLabel(contract.getRequiredSigningMethod());
        addMetaCell(meta, "Signing method", method != null ? method : "Simple electronic signature");
        if (contract.isAutoRenew())
            addMetaCell(meta, "Renewal", "Renews automatically (notice " + contract.getRenewalNoticeDays() + " days)");
        if (contract.getNotes() != null && !contract.getNotes().isBlank())
            addMetaCell(meta, "Notes", contract.getNotes());
        // keep the grid rectangular: pad the last row
        int cells = 6 + (contract.isAutoRenew() ? 1 : 0) + (contract.getNotes() != null && !contract.getNotes().isBlank() ? 1 : 0);
        for (int k = cells % 3; k != 0 && k < 3; k++) {
            PdfPCell pad = new PdfPCell();
            pad.setBorder(Rectangle.NO_BORDER);
            pad.setBackgroundColor(LIGHT_GRAY);
            meta.addCell(pad);
        }
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
        heading.setSpacingAfter(4);
        doc.add(heading);
        // Formatting from the editor (bold, italic, headings, lists, tables, quotes) is kept; see ContractPdfBody.
        for (Element e : ContractPdfBody.render(htmlBody)) doc.add(e);
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
            partyBox.setKeepTogether(true);

            // Left: party details
            PdfPCell left = new PdfPCell();
            left.setBorder(Rectangle.NO_BORDER);
            left.setBackgroundColor(LIGHT_GRAY);
            left.setPadding(12);
            left.addElement(new Paragraph(nice(party.getPartyRole()).toUpperCase(Locale.ROOT), ROLE_FONT));
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

                Paragraph dateP = new Paragraph(party.getSignedAt() != null ? DT.format(party.getSignedAt()) : "", META_SMALL);
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

    // ── Fingerprint and certificate of signing ──────────────────────────────

    /** The SHA-256 of the contract text locked when it was sent, so anyone holding a copy can check it against HandyFlow's record. */
    private void addFingerprint(Document doc, Contract contract) throws DocumentException {
        if (contract.getBodyHash() == null || contract.getBodyHash().isBlank()) return;
        Paragraph p = new Paragraph("Fingerprint of the agreed contract text (SHA-256): " + contract.getBodyHash(), HASH_FONT);
        p.setSpacingBefore(10);
        doc.add(p);
    }

    /**
     * Starts a new page that records how each party signed. Only for a contract somebody has signed: an unsigned draft carries no
     * claim that it was signed. It states plainly what the evidence is (a one-time code sent to the signer, the time, address and
     * device) and that the signature is a simple electronic one; any other method the contract requires is completed separately.
     */
    private void addCertificate(Document doc, Contract contract, List<ContractSignature> signatures) throws DocumentException {
        if (signatures == null || signatures.isEmpty()) return;
        doc.newPage();
        Paragraph title = new Paragraph("Certificate of signing", TITLE_FONT);
        title.setSpacingAfter(4);
        doc.add(title);
        Paragraph ref = new Paragraph(contract.getContractNumber() + " \u00b7 " + contract.getTitle(), PARTIES_FONT);
        ref.setSpacingAfter(8);
        doc.add(ref);
        Paragraph intro = new Paragraph("This page records how and when each party signed. HandyFlow generated it from the signing "
                + "records for this contract. Each signer opened their own signing link, confirmed a one-time code and chose to sign.", AUDIT_INTRO);
        intro.setSpacingAfter(10);
        doc.add(intro);

        PdfPTable fp = new PdfPTable(1);
        fp.setWidthPercentage(100);
        fp.setSpacingAfter(10);
        PdfPCell fpc = new PdfPCell();
        fpc.setBorder(Rectangle.NO_BORDER);
        fpc.setBackgroundColor(LIGHT_GRAY);
        fpc.setPadding(10);
        fpc.addElement(new Paragraph("FINGERPRINT OF THE AGREED CONTRACT TEXT (SHA-256)", LABEL_FONT));
        String hash = contract.getBodyHash() == null || contract.getBodyHash().isBlank() ? "Not recorded" : contract.getBodyHash();
        Paragraph hp = new Paragraph(hash, MONO_FONT);
        hp.setSpacingBefore(3);
        fpc.addElement(hp);
        if (contract.getBodyLockedAt() != null) {
            Paragraph locked = new Paragraph("Text locked when sent for signing: " + DT.format(contract.getBodyLockedAt()), META_SMALL);
            locked.setSpacingBefore(3);
            fpc.addElement(locked);
        }
        fp.addCell(fpc);
        doc.add(fp);

        List<ContractParty> parties = contract.getParties();
        for (ContractSignature sig : signatures) {
            ContractParty party = parties.stream().filter(p -> p.getId().equals(sig.getPartyId())).findFirst().orElse(null);
            PdfPTable t = new PdfPTable(2);
            t.setWidthPercentage(100);
            t.setWidths(new float[]{1, 3});
            t.setSpacingAfter(8);
            t.setKeepTogether(true);
            String who = party == null ? "Unknown signer" : party.getFullName();
            PdfPCell head = new PdfPCell(new Phrase(who + (party != null && party.getPartyRole() != null ? "  \u00b7  " + nice(party.getPartyRole()) : ""), AUDIT_HEADER));
            head.setColspan(2);
            head.setBackgroundColor(BRAND_DARK);
            head.setBorder(Rectangle.NO_BORDER);
            head.setPadding(6);
            t.addCell(head);
            certRow(t, "Company", party != null && party.getCompanyName() != null ? party.getCompanyName() : "\u2014");
            certRow(t, "Email", party != null && party.getEmail() != null ? party.getEmail() : "\u2014");
            certRow(t, "Signed at", sig.getSignedAt() != null ? DT.format(sig.getSignedAt()) : "\u2014");
            certRow(t, "Identity check", "One-time code" + (sig.getPhoneLast4() != null ? " sent to phone ending " + sig.getPhoneLast4() : ""));
            certRow(t, "IP address", formatIp(sig.getIpAddress()));
            String ua = sig.getUserAgent();
            certRow(t, "Device", ua == null || ua.isBlank() ? "\u2014" : ua.length() > 110 ? ua.substring(0, 110) + "\u2026" : ua);
            doc.add(t);
        }

        String method = methodLabel(contract.getRequiredSigningMethod());
        Paragraph what = new Paragraph("These are simple electronic signatures. "
                + (method != null && !"Simple electronic signature".equals(method)
                ? "This contract asks for: " + method + ". That has to be completed separately; this certificate does not replace it. " : "")
                + "To check a copy, compare its text with the fingerprint above or ask the sender to confirm the fingerprint in HandyFlow.", AUDIT_INTRO);
        what.setSpacingBefore(4);
        doc.add(what);

        Paragraph legal = new Paragraph(
                "This contract was electronically signed via HandyFlow (handyflow.co.za). " +
                        "Electronic signatures on this document are valid and legally binding in terms of " +
                        "Section 13 of the Electronic Communications and Transactions Act 25 of 2002 (ECTA). " +
                        "The audit trail above constitutes sufficient evidence of identity and intention to sign.",
                LEGAL_FONT);
        legal.setSpacingBefore(14);
        doc.add(legal);
    }

    private void certRow(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, LABEL_FONT));
        l.setBorder(Rectangle.NO_BORDER);
        l.setBackgroundColor(LIGHT_GRAY);
        l.setPadding(5);
        t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(value, AUDIT_CELL));
        v.setBorder(Rectangle.NO_BORDER);
        v.setBackgroundColor(LIGHT_GRAY);
        v.setPadding(5);
        t.addCell(v);
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
        private final String watermark;   // null for a document that should not be stamped
        private PdfTemplate total;
        FooterHandler(String contractNumber, String watermark) { this.contractNumber = contractNumber; this.watermark = watermark; }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            // Reserve space for the page total; it is filled in when the document closes.
            total = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                PdfContentByte cb = writer.getDirectContent();
                String left = "HandyFlow \u00b7 " + contractNumber + " \u00b7 Page " + writer.getPageNumber() + " of ";
                float y = document.bottomMargin() - 20;
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(left, FOOTER_FONT),
                        document.leftMargin(), y, 0);
                float w = FOOTER_FONT.getCalculatedBaseFont(false).getWidthPoint(left, FOOTER_FONT.getCalculatedSize());
                cb.addTemplate(total, document.leftMargin() + w, y);
                ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                        new Phrase("Generated " + DT.format(java.time.Instant.now()), FOOTER_FONT),
                        document.getPageSize().getWidth() - document.rightMargin(), y, 0);
                if (watermark != null) stamp(writer, document);
            } catch (Exception ignored) {
                // A broken footer must never take down PDF generation for a
                // document that otherwise generated correctly.
            }
        }

        /** A large pale diagonal word behind the text. Drawn in the layer under the content so it never hides anything. */
        private void stamp(PdfWriter writer, Document document) throws Exception {
            PdfContentByte under = writer.getDirectContentUnder();
            under.saveState();
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(0.08f);
            under.setGState(gs);
            under.beginText();
            under.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false), 120);
            under.setColorFill(MID_GRAY);
            under.showTextAligned(Element.ALIGN_CENTER, watermark,
                    document.getPageSize().getWidth() / 2, document.getPageSize().getHeight() / 2, 45);
            under.endText();
            under.restoreState();
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            try {
                ColumnText.showTextAligned(total, Element.ALIGN_LEFT,
                        new Phrase(String.valueOf(writer.getPageNumber() - 1), FOOTER_FONT), 0, 0, 0);
            } catch (Exception ignored) {
                // See onEndPage.
            }
        }
    }
}