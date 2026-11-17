package za.co.handyflow.platform.supplychain.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.GoodsReceivingService;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierDocType;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierDocument;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScSupplierDocumentTest {

    private ScSupplierDocument doc(LocalDate expiry) {
        return ScSupplierDocument.create(UUID.randomUUID(), UUID.randomUUID(), SupplierDocType.TAX_CLEARANCE,
                "TCS-1", LocalDate.of(2026, 1, 1), expiry, UUID.randomUUID(), "Tester");
    }

    @Test void newDocumentIsActive() {
        assertTrue(doc(LocalDate.of(2027, 1, 1)).isActive());
    }

    @Test void supersededOrRemovedIsNotActive() {
        ScSupplierDocument d = doc(null);
        d.supersede();
        assertFalse(d.isActive());
        assertEquals(ScSupplierDocument.SUPERSEDED, d.getStatus());
    }

    @Test void removingTwiceIsRefused() {
        ScSupplierDocument d = doc(null);
        d.remove("uploaded in error");
        assertThrows(IllegalStateException.class, () -> d.remove("again"));
    }

    @Test void daysToExpiryCountsFromGivenDate() {
        assertEquals(Long.valueOf(10), doc(LocalDate.of(2026, 11, 27)).daysToExpiry(LocalDate.of(2026, 11, 17)));
    }

    @Test void expiryRequiredTypes() {
        assertTrue(SupplierDocType.TAX_CLEARANCE.isExpiryRequired());
        assertFalse(SupplierDocType.COMPANY_REGISTRATION.isExpiryRequired());
    }

    @Test void overReceiptLimitHonoursTolerance() {
        assertEquals(0, new BigDecimal("10").compareTo(
                GoodsReceivingService.maxReceivable(new BigDecimal("10"), BigDecimal.ZERO)));
        assertEquals(0, new BigDecimal("11").compareTo(
                GoodsReceivingService.maxReceivable(new BigDecimal("10"), new BigDecimal("10"))));
    }
}
