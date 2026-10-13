package za.co.handyflow.platform.debtcollection.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsAndEmailHelpersTest {

    @Test
    void jsonTemplateEscapesSoMessageTextCannotBreakTheRequest() throws Exception {
        String out = JsonTemplate.render("{\"to\":\"{{to}}\",\"body\":\"{{body}}\"}",
                Map.of("to", "+27821234567", "body", "He said \"pay\"\nnow \\ ok"));
        var tree = new ObjectMapper().readTree(out); // must still be valid JSON with exactly the same text
        assertEquals("He said \"pay\"\nnow \\ ok", tree.get("body").asText());
        assertEquals(2, tree.size());
    }

    @Test
    void jsonTemplateInjectionAttemptStaysInsideTheString() throws Exception {
        String out = JsonTemplate.render("{\"body\":\"{{body}}\"}", Map.of("body", "x\",\"admin\":\"true"));
        var tree = new ObjectMapper().readTree(out);
        assertEquals(1, tree.size());
        assertEquals("x\",\"admin\":\"true", tree.get("body").asText());
    }

    @Test
    void jsonTemplateRejectsUnknownPlaceholders() {
        assertThrows(IllegalArgumentException.class, () -> JsonTemplate.render("{\"a\":\"{{nope}}\"}", Map.of("to", "1")));
    }

    @Test
    void jsonPathsWalksObjectsAndArrays() throws Exception {
        var n = new ObjectMapper().readTree("{\"messages\":[{\"id\":\"m-1\"},{\"id\":\"m-2\"}],\"count\":2}");
        assertEquals("m-1", JsonPaths.text(n, "messages.0.id"));
        assertEquals("m-2", JsonPaths.text(n, "messages.1.id"));
        assertEquals("2", JsonPaths.text(n, "count"));
        assertNull(JsonPaths.text(n, "messages.5.id"));
        assertNull(JsonPaths.text(n, "messages"));  // containers are not ids
        assertNull(JsonPaths.text(n, "nope.x"));
        assertNull(JsonPaths.text(n, ""));
    }

    @Test
    void emailBodyIsEscapedAndKeepsLineBreaks() {
        String html = EmailBodyHtml.toHtml("Dear <b>Mr</b> & \"Co\"\nline2");
        assertTrue(html.contains("&lt;b&gt;Mr&lt;/b&gt; &amp; &quot;Co&quot;\nline2"));
        assertFalse(html.contains("<b>"));
        assertTrue(html.contains("white-space:pre-wrap"));
    }

    @Test
    void smsNumberFormats() {
        assertEquals("+27821234567", HttpSmsSender.formatNumber("082 123 4567", "E164_PLUS"));
        assertEquals("+27821234567", HttpSmsSender.formatNumber("+27 82 123 4567", null));
        assertEquals("27821234567", HttpSmsSender.formatNumber("0821234567", "DIGITS"));
        assertEquals("0821234567", HttpSmsSender.formatNumber("+27821234567", "LOCAL"));
    }
}
