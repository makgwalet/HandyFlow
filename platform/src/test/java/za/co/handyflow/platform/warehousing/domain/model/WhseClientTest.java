package za.co.handyflow.platform.warehousing.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Unlike CollAgencyClient, WhseClient carries no trust balance — this test intentionally has no equivalent of the overdraw-guard tests, since there's no money held here at all, only goods (see WhseClient's own Javadoc). */
class WhseClientTest {

    private WhseClient newClient() {
        return WhseClient.create(UUID.randomUUID(), "Acme Retailers", "REG123", null, null, null, null, "Jane",
                "jane@acme.co.za", "0821234567", "1 Main St");
    }

    @Test
    void newClientStartsActive() {
        assertEquals("ACTIVE", newClient().getStatus());
        assertFalse(newClient().isDeleted());
    }

    @Test
    void deactivateAndReactivateToggleStatus() {
        WhseClient c = newClient();
        c.deactivate();
        assertEquals("INACTIVE", c.getStatus());
        c.reactivate();
        assertEquals("ACTIVE", c.getStatus());
    }

    @Test
    void softDeleteSetsDeletedAt() {
        WhseClient c = newClient();
        c.softDelete();
        assertTrue(c.isDeleted());
        assertNotNull(c.getDeletedAt());
    }
}
