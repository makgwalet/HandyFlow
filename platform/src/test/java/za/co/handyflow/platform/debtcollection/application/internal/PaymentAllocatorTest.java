package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentAllocatorTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void defaultPutsEverythingOnPrincipal() {
        var s = PaymentAllocator.split(d("500.00"), d("120.00"), false, null);
        assertEquals(d("500.00"), s.principal());
        assertEquals(d("0"), s.fees().stripTrailingZeros().equals(BigDecimal.ZERO) ? d("0") : s.fees());
    }

    @Test
    void feesFirstSettlesFeesUpToWhatIsOwed() {
        var s = PaymentAllocator.split(d("500.00"), d("120.00"), true, null);
        assertEquals(d("120.00"), s.fees());
        assertEquals(d("380.00"), s.principal());
        var small = PaymentAllocator.split(d("50.00"), d("120.00"), true, null);
        assertEquals(d("50.00"), small.fees());
        assertEquals(0, small.principal().signum());
    }

    @Test
    void explicitFeesAreHonouredWithinLimits() {
        var s = PaymentAllocator.split(d("500.00"), d("120.00"), false, d("30.00"));
        assertEquals(d("30.00"), s.fees());
        assertEquals(d("470.00"), s.principal());
        assertThrows(IllegalArgumentException.class, () -> PaymentAllocator.split(d("500.00"), d("120.00"), false, d("130.00")));
        assertThrows(IllegalArgumentException.class, () -> PaymentAllocator.split(d("20.00"), d("120.00"), false, d("30.00")));
        assertThrows(IllegalArgumentException.class, () -> PaymentAllocator.split(d("500.00"), d("120.00"), false, d("-1")));
    }

    @Test
    void splitAlwaysSumsToTheAmount() {
        var s = PaymentAllocator.split(d("333.33"), d("100.10"), true, null);
        assertEquals(d("333.33"), s.principal().add(s.fees()));
    }

    @Test
    void nonPositiveAmountRejected() {
        assertThrows(IllegalArgumentException.class, () -> PaymentAllocator.split(BigDecimal.ZERO, d("1"), false, null));
    }
}
