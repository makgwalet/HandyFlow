package za.co.handyflow.platform.collectionsagency.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** trustBalance is money that belongs to the client, not the agency — the overdraw guard is what keeps a remittance from ever exceeding what's actually held. */
class CollAgencyClientTest {

    private CollAgencyClient newClient() {
        return CollAgencyClient.create(UUID.randomUUID(), "Acme Retailers", "REG123",
                new BigDecimal("20.00"), "Jane", "jane@acme.co.za", "0821234567", "1 Main St");
    }

    @Test
    void newClientStartsWithZeroTrustBalance() {
        assertEquals(BigDecimal.ZERO, newClient().getTrustBalance());
    }

    @Test
    void increaseTrustBalanceAddsAmount() {
        CollAgencyClient c = newClient();
        c.increaseTrustBalance(new BigDecimal("1000.00"));
        assertEquals(new BigDecimal("1000.00"), c.getTrustBalance());
    }

    @Test
    void increaseTrustBalanceRejectsNonPositiveAmount() {
        CollAgencyClient c = newClient();
        assertThrows(IllegalArgumentException.class, () -> c.increaseTrustBalance(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> c.increaseTrustBalance(new BigDecimal("-5")));
    }

    @Test
    void decreaseTrustBalanceSubtractsAmount() {
        CollAgencyClient c = newClient();
        c.increaseTrustBalance(new BigDecimal("1000.00"));
        c.decreaseTrustBalance(new BigDecimal("400.00"));
        assertEquals(new BigDecimal("600.00"), c.getTrustBalance());
    }

    @Test
    void decreaseTrustBalanceRejectsOverdraw() {
        CollAgencyClient c = newClient();
        c.increaseTrustBalance(new BigDecimal("500.00"));
        assertThrows(IllegalStateException.class, () -> c.decreaseTrustBalance(new BigDecimal("500.01")));
    }

    @Test
    void decreaseTrustBalanceToExactlyZeroSucceeds() {
        CollAgencyClient c = newClient();
        c.increaseTrustBalance(new BigDecimal("500.00"));
        c.decreaseTrustBalance(new BigDecimal("500.00"));
        assertEquals(BigDecimal.ZERO, c.getTrustBalance());
    }

    @Test
    void deactivateAndReactivateToggleStatus() {
        CollAgencyClient c = newClient();
        assertEquals("ACTIVE", c.getStatus());
        c.deactivate();
        assertEquals("INACTIVE", c.getStatus());
        c.reactivate();
        assertEquals("ACTIVE", c.getStatus());
    }
}
