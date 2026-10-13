package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateRendererTest {

    @Test
    void rendersBuiltInAndCustomPlaceholders() {
        String out = TemplateRenderer.render("Dear {{ debtorName }}, case {{caseNumber}}: {{custom.note}}",
                Map.of("debtorName", "ABC Trading", "caseNumber", "DC-00001", "custom.note", "pay by Friday"));
        assertEquals("Dear ABC Trading, case DC-00001: pay by Friday", out);
    }

    @Test
    void replacementTextWithDollarAndBackslashIsLiteral() {
        assertEquals("Amount: R$1\\2", TemplateRenderer.render("Amount: {{totalOutstanding}}", Map.of("totalOutstanding", "R$1\\2")));
    }

    @Test
    void missingValueThrowsInsteadOfSendingBrokenText() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> TemplateRenderer.render("Hi {{debtorName}} {{custom.x}}", Map.of("debtorName", "A")));
        assertTrue(ex.getMessage().contains("custom.x"));
    }

    @Test
    void renderableOnlyForBuiltInOrCustomNamespace() {
        assertTrue(TemplateRenderer.isRenderable("{{debtorName}} {{custom.foo}}"));
        assertTrue(TemplateRenderer.isRenderable(null));
        assertFalse(TemplateRenderer.isRenderable("{{somethingElse}}"));
    }
}
