package za.co.handyflow.platform.contracting.application.internal;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractParty;
import za.co.handyflow.platform.contracting.domain.model.ContractSignature;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractPdfGeneratorTest {

    ContractPdfGenerator gen = new ContractPdfGenerator(null);
    TenantId tenant = TenantId.generate();

    Contract contract(String body) {
        return Contract.create(tenant, "CTR-2026-0001", "Service agreement", "SERVICE", body, null, UUID.randomUUID());
    }

    void assertPdf(byte[] pdf) {
        assertNotNull(pdf);
        assertTrue(pdf.length > 1000, "a rendered contract is more than a stub");
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));
        String tail = new String(pdf, Math.max(0, pdf.length - 32), Math.min(32, pdf.length), StandardCharsets.US_ASCII);
        assertTrue(tail.contains("%%EOF"), "the document is complete");
    }

    @Test
    void rendersAContractWithTenantNameAndVat() {
        assertPdf(gen.generate(contract("<p>Hello <b>world</b></p><ul><li>One</li></ul>"), List.of(), "Acme (Pty) Ltd", "4123456789"));
    }

    @Test
    void rendersWithoutAVatNumber() {
        assertPdf(gen.generate(contract("<p>Terms</p>"), List.of(), "Acme (Pty) Ltd", null));
    }

    @Test
    void rendersAnEmptyOrMissingBody() {
        assertPdf(gen.generate(contract(""), List.of(), "Acme", null));
        assertPdf(gen.generate(contract(null), List.of(), "Acme", null));
    }

    @Test
    void scriptAndMarkupInTheBodyDoNotBreakRendering() {
        assertPdf(gen.generate(contract("<p>ok</p><script>alert(1)</script><table><tr><td>cell</td></tr></table>"), List.of(), "Acme", null));
    }

    @Test
    void aLongContractRunsToSeveralPages() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++) sb.append("<p>Clause ").append(i).append(": the parties agree to the terms set out in this paragraph.</p>");
        byte[] small = gen.generate(contract("<p>Short</p>"), List.of(), "Acme", null);
        byte[] large = gen.generate(contract(sb.toString()), List.of(), "Acme", null);
        assertPdf(large);
        assertTrue(large.length > small.length);
    }

    // ── what the PDF actually says ───────────────────────────────────────────

    /** All the text of the PDF, page by page joined, so the tests can check content and not only that a file came out. */
    String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor x = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= reader.getNumberOfPages(); i++) sb.append(x.getTextFromPage(i)).append('\n');
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    ContractParty party(Contract c, String role, String name, String company, int order) {
        ContractParty p = ContractParty.create(tenant, c.getId(), "COMPANY", role, name, name.toLowerCase().replace(' ', '.') + "@example.com", "0821234567", company, order);
        c.getParties().add(p);
        return p;
    }

    Contract signedContract() {
        Contract c = contract("<p>Terms</p>");
        ContractParty a = party(c, "CLIENT", "Ann Client", "Acme (Pty) Ltd", 1);
        ContractParty b = party(c, "SUPPLIER", "Bob Supplier", "Beta CC", 2);
        c.lockBody("a".repeat(64));
        c.send();
        a.markSigned("41.0.0.1", "Mozilla/5.0 Test");
        b.markSigned("41.0.0.2", "Mozilla/5.0 Other");
        c.markSigned();
        return c;
    }

    List<ContractSignature> signaturesOf(Contract c) {
        return c.getParties().stream().map(p -> ContractSignature.create(tenant, c.getId(), p.getId(), "hash", "1234", p.getSignIpAddress(), p.getSignUserAgent(), null, null)).toList();
    }

    @Test
    void formattingFromTheEditorIsKept() throws Exception {
        String body = "<h2>1. Payment</h2><p>Pay <strong>within 30 days</strong> of an <em>invoice</em>.</p>"
                + "<ul><li>First duty</li><li>Second duty<ul><li>Nested duty</li></ul></li></ul>"
                + "<ol><li>Step one</li><li>Step two</li></ol>"
                + "<table><thead><tr><th>Item</th><th>Price</th></tr></thead><tbody><tr><td>Widget</td><td>R 100.00</td></tr></tbody></table>"
                + "<blockquote>Quoted wording</blockquote><hr><p>H<sub>2</sub>O and x<sup>2</sup></p>";
        String t = text(gen.generate(contract(body), List.of(), "Acme", null));
        for (String expected : List.of("1. Payment", "within 30 days", "invoice", "First duty", "Second duty", "Nested duty", "Step one", "Step two",
                "Item", "Price", "Widget", "R 100.00", "Quoted wording"))
            assertTrue(t.contains(expected), "missing: " + expected + "\n" + t);
        assertTrue(t.contains("\u2022"), "bullets are drawn");
    }

    @Test
    void aTableWithUnevenRowsAndSpansStillRenders() {
        String body = "<table><tr><td colspan=\"2\">Wide</td></tr><tr><td>One</td></tr><tr><td>A</td><td>B</td><td>C</td></tr></table>"
                + "<table></table><table><tr></tr></table><table><tr><td rowspan=\"2\">Tall</td><td>x</td></tr><tr><td>y</td></tr></table>";
        assertPdf(gen.generate(contract(body), List.of(), "Acme", null));
    }

    @Test
    void deeplyNestedMarkupCannotExhaustTheStack() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) sb.append("<div>");
        sb.append("deep text");
        for (int i = 0; i < 2000; i++) sb.append("</div>");
        assertPdf(gen.generate(contract(sb.toString()), List.of(), "Acme", null));
        StringBuilder lists = new StringBuilder();
        for (int i = 0; i < 500; i++) lists.append("<ul><li>x");
        assertPdf(gen.generate(contract(lists.toString()), List.of(), "Acme", null));
    }

    @Test
    void aDraftIsStampedAndMakesNoClaimThatItWasSigned() throws Exception {
        Contract c = contract("<p>Terms</p>");
        party(c, "CLIENT", "Ann Client", "Acme (Pty) Ltd", 1);
        String t = text(gen.generate(c, List.of(), "Acme (Pty) Ltd", "4123456789"));
        assertTrue(t.contains("DRAFT"));
        assertTrue(t.contains("Between"));
        assertTrue(t.contains("Not yet signed"));
        assertFalse(t.contains("Certificate of signing"));
        assertFalse(t.contains("electronically signed"), "an unsigned document must not say it was signed");
    }

    @Test
    void aSentContractShowsItIsAwaitingSignatures() throws Exception {
        Contract c = contract("<p>Terms</p>");
        party(c, "CLIENT", "Ann Client", "Acme", 1);
        c.lockBody("b".repeat(64));
        c.send();
        String t = text(gen.generate(c, List.of(), "Acme", null));
        assertTrue(t.contains("AWAITING SIGNATURES"));
        assertTrue(t.contains("b".repeat(64)), "the fingerprint is shown");
        assertFalse(t.contains("Certificate of signing"));
    }

    @Test
    void aSignedContractGetsACertificateWithTheEvidence() throws Exception {
        Contract c = signedContract();
        String t = text(gen.generate(c, signaturesOf(c), "Acme (Pty) Ltd", null));
        assertTrue(t.contains("Certificate of signing"));
        assertTrue(t.contains("a".repeat(64)), "the fingerprint of the agreed text");
        for (String expected : List.of("Ann Client", "Bob Supplier", "41.0.0.1", "41.0.0.2", "Mozilla/5.0 Test", "One-time code", "ending 1234", "simple electronic signatures"))
            assertTrue(t.contains(expected), "missing: " + expected);
        assertFalse(t.contains("DRAFT"), "a signed contract is not stamped as a draft");
        assertTrue(t.contains("electronically signed via HandyFlow"));
    }

    @Test
    void theCertificateSaysWhenAnotherSigningMethodIsStillNeeded() throws Exception {
        Contract c = signedContract();
        c.setRequiredSigningMethod("WET_INK");
        String t = text(gen.generate(c, signaturesOf(c), "Acme", null));
        assertTrue(t.contains("Wet-ink signature"));
        assertTrue(t.contains("does not replace it"));
    }

    @Test
    void valueAndCurrencyAreShownAsRecorded() throws Exception {
        Contract c = contract("<p>Terms</p>");
        c.setValueAmount(new java.math.BigDecimal("12345.5"));
        assertTrue(text(gen.generate(c, List.of(), "Acme", null)).contains("R 12,345.50"));
    }
}
