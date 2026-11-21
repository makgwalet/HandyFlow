package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditJsonTest {
    @Test void plain() {
        assertEquals("{\"status\":\"SUSPENDED\",\"note\":\"late\"}", AuditJson.statusChange("SUSPENDED", "late"));
    }
    @Test void nullNote() {
        assertEquals("{\"status\":\"ACTIVE\",\"note\":null}", AuditJson.statusChange("ACTIVE", null));
    }
    @Test void escapesQuoteBackslashNewlineTab() {
        assertEquals("\"a\\\"b\\\\c\\nd\\te\"", AuditJson.quote("a\"b\\c\nd\te"));
    }
    @Test void escapesOtherControlChars() {
        assertEquals("\"\\u0001\"", AuditJson.quote("\u0001"));
    }
}
