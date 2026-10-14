package za.co.handyflow.platform.accounting.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;

/** Finance patch F3: keys that identify the business event behind a journal. */
class PostingKeysTest {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void buildsModuleEventId() {
        assertEquals("invoicing:invoice-issued:" + id, PostingKeys.of("invoicing", "invoice-issued", id));
        assertEquals("ap:bill-paid:42", PostingKeys.of("ap", "bill-paid", 42));
    }
    @Test void sameEventSameKey() {
        assertEquals(PostingKeys.of("expenses", "claim-approved", id), PostingKeys.of("expenses", "claim-approved", id));
    }
    @Test void refusesBadParts() {
        assertThrows(IllegalArgumentException.class, () -> PostingKeys.of("AP", "bill-paid", id));
        assertThrows(IllegalArgumentException.class, () -> PostingKeys.of("ap", "bill paid", id));
        assertThrows(IllegalArgumentException.class, () -> PostingKeys.of("ap", "bill-paid-", id));
        assertThrows(IllegalArgumentException.class, () -> PostingKeys.of("ap:x", "bill-paid", id));
        assertThrows(IllegalArgumentException.class, () -> PostingKeys.of("ap", "bill-paid", "  "));
        assertThrows(NullPointerException.class, () -> PostingKeys.of("ap", "bill-paid", null));
    }
    @Test void checksLength() {
        assertThrows(IllegalArgumentException.class, () -> PostingKeys.check(null));
        assertThrows(IllegalArgumentException.class, () -> PostingKeys.check(" "));
        assertThrows(IllegalArgumentException.class, () -> PostingKeys.check("x".repeat(201)));
        assertEquals("x".repeat(200), PostingKeys.check("x".repeat(200)));
    }
}
