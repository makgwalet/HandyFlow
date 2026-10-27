package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FmInvoicePaymentTest {

    private static final LocalDate TODAY = LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg"));

    private FmInvoicePayment pay(BigDecimal amount, LocalDate date, String method, String reference, String key) {
        return FmInvoicePayment.create(TenantId.generate(), UUID.randomUUID(), amount, date, method, reference, UUID.randomUUID(), key);
    }

    @Test
    void aPaymentDefaultsToTodayAndStartsPendingForTheLedger() {
        FmInvoicePayment p = pay(new BigDecimal("100.00"), null, null, null, null);
        assertEquals(TODAY, p.getPaymentDate());
        assertEquals("PENDING", p.getGlStatus());
        assertNull(p.getMethod());
        assertNull(p.getIdempotencyKey());
    }

    @Test
    void theIdempotencyKeyIsTrimmedAndBlankMeansNone() {
        assertEquals("abc-123", pay(BigDecimal.TEN, null, null, null, "  abc-123 ").getIdempotencyKey());
        assertNull(pay(BigDecimal.TEN, null, null, null, "   ").getIdempotencyKey());
    }

    @Test
    void theMethodIsNormalisedAndMustBeKnown() {
        assertEquals("EFT", pay(BigDecimal.TEN, null, " eft ", null, null).getMethod());
        assertThrows(IllegalArgumentException.class, () -> pay(BigDecimal.TEN, null, "BITCOIN", null, null));
    }

    @Test
    void aPaymentCannotBeDatedInTheFuture() {
        assertThrows(IllegalArgumentException.class, () -> pay(BigDecimal.TEN, TODAY.plusDays(1), null, null, null));
        assertEquals(TODAY, pay(BigDecimal.TEN, TODAY, null, null, null).getPaymentDate());
    }

    @Test
    void theAmountMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> pay(BigDecimal.ZERO, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> pay(null, null, null, null, null));
    }

    @Test
    void theReferenceIsTrimmedAndBounded() {
        assertEquals("EFT-77", pay(BigDecimal.TEN, null, null, " EFT-77 ", null).getReference());
        assertNull(pay(BigDecimal.TEN, null, null, "  ", null).getReference());
        assertThrows(IllegalArgumentException.class, () -> pay(BigDecimal.TEN, null, null, "x".repeat(101), null));
    }
}
