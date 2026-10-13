package za.co.handyflow.platform.clinic.application.internal.document;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The reference pack through the iText adapter. Needs the iText jars, so it runs under Maven only. The same loop is the template for the CLIN-DOC-001A spike with any other renderer. */
class ITextDocumentRendererTest {

    private final DocumentRenderer renderer = new ITextDocumentRenderer();

    @Test void everyReferenceDocumentRendersToAPdf() {
        ReferenceDocuments.all().forEach((name, model) -> {
            byte[] pdf = renderer.render(model);
            assertTrue(pdf.length > 1000, name);
            assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII), name);
        });
    }

    @Test void theLongReportNeedsMoreThanOnePage() {
        byte[] pdf = renderer.render(ReferenceDocuments.all().get("medical-report-tables"));
        String raw = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(raw.split("/Type\\s*/Page[^s]", -1).length - 1 >= 2, "two or more pages");
    }

    @Test void anInvalidModelIsRefusedBeforeRendering() {
        var bad = new DocumentModel("", null, List.of(), null, false);
        assertThrows(IllegalArgumentException.class, () -> renderer.render(bad));
    }

    @Test void theNameIsStable() {
        assertEquals("itext-7.2.5", renderer.name());
    }
}
