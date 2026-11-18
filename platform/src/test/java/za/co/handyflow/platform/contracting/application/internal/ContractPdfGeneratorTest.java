package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.Contract;
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
}
