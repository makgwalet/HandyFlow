package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.ProfitRules;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ProfitRulesTest {
    static BigDecimal n(String s) { return new BigDecimal(s); }

    @Test void marginIsRevenueLessCost() {
        assertEquals(n("700.00"), ProfitRules.margin(n("1000.00"), n("300.00")));
        assertEquals(n("-300"), ProfitRules.margin(null, n("300")));
        assertEquals(n("1000"), ProfitRules.margin(n("1000"), null));
    }
    @Test void marginPercentIsOfRevenueToOneDecimal() {
        assertEquals(n("70.0"), ProfitRules.marginPercent(n("1000"), n("700")));
        assertEquals(n("33.3"), ProfitRules.marginPercent(n("300"), n("100")));
        assertEquals(n("-25.0"), ProfitRules.marginPercent(n("400"), n("-100")));
    }
    @Test void noRevenueMeansNoPercentage() {
        assertNull(ProfitRules.marginPercent(BigDecimal.ZERO, n("-300")));
        assertNull(ProfitRules.marginPercent(null, n("-300")));
    }
    @Test void thePeriodMustBeSensible() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertDoesNotThrow(() -> ProfitRules.checkRange(d, d));
        assertDoesNotThrow(() -> ProfitRules.checkRange(d, d.plusYears(2).minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> ProfitRules.checkRange(d, d.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> ProfitRules.checkRange(d, d.plusYears(3)));
        assertThrows(IllegalArgumentException.class, () -> ProfitRules.checkRange(null, d));
    }
}
