package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlSanitizerTest {

    @Test
    void removesScriptHandlersAndImages() {
        String out = HtmlSanitizer.clean("<p onclick=\"x()\">Hi</p><script>alert(1)</script><img src=x onerror=alert(1)>");
        assertEquals("<p>Hi</p>", out);
    }

    @Test
    void removesLinksFramesAndStyles() {
        String out = HtmlSanitizer.clean("<a href=\"javascript:alert(1)\">x</a><iframe src=\"//evil\"></iframe>"
                + "<p style=\"background:url(x)\">ok</p>");
        assertFalse(out.contains("javascript"));
        assertFalse(out.contains("iframe"));
        assertFalse(out.contains("style"));
        assertTrue(out.contains("<p>ok</p>"));
    }

    @Test
    void keepsSeededTemplateFormattingAndPlaceholders() {
        String html = "<h2>1. Parties</h2><p><strong>{{employee_name}}</strong></p><ul><li>a</li></ul>";
        assertEquals(html, HtmlSanitizer.clean(html));
    }

    @Test
    void nullStaysNull() {
        assertNull(HtmlSanitizer.clean(null));
    }
}
