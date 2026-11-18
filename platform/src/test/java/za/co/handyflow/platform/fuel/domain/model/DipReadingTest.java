package za.co.handyflow.platform.fuel.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

/** Written, not compiled or run. */
class DipReadingTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }
    static DipReading dip(String actual, String book) {
        return DipReading.create(T, UUID.randomUUID(), Instant.now(), bd(actual), book == null ? null : bd(book), "Sipho", null);
    }

    @Test @DisplayName("a tank below its book level has a negative variance, and that is the loss")
    void shortfallIsNegative() {
        DipReading d = dip("4900", "5000");
        assertEquals(0, bd("-100").compareTo(d.getVarianceLitres()));
        assertTrue(d.hasNegativeVariance());
    }

    @Test @DisplayName("a tank above its book level has a positive variance and is not a loss")
    void surplusIsPositive() {
        DipReading d = dip("5100", "5000");
        assertEquals(0, bd("100").compareTo(d.getVarianceLitres()));
        assertFalse(d.hasNegativeVariance());
    }

    @Test @DisplayName("a tank on its book level has no variance")
    void exactMatchIsZero() {
        DipReading d = dip("5000", "5000");
        assertEquals(0, BigDecimal.ZERO.compareTo(d.getVarianceLitres()));
        assertFalse(d.hasNegativeVariance());
    }

    @Test @DisplayName("with no book figure there is no variance to judge")
    void noBookNoVariance() {
        DipReading d = dip("5000", null);
        assertNull(d.getVarianceLitres());
        assertFalse(d.hasNegativeVariance());
    }
}
