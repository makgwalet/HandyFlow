package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.BillingRules;
import za.co.handyflow.platform.earthmoving.domain.model.BillingRules.Cover;
import za.co.handyflow.platform.earthmoving.domain.model.BillingRules.Span;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BillingRulesTest {
    static final YearMonth SEPT = YearMonth.of(2026, 9);
    static LocalDate d(String s) { return LocalDate.parse(s); }

    @Test void aMonthIsGivenAsYearAndMonth() {
        assertEquals(SEPT, BillingRules.month(" 2026-09 "));
        assertThrows(IllegalArgumentException.class, () -> BillingRules.month("September"));
        assertThrows(IllegalArgumentException.class, () -> BillingRules.month(null));
        assertThrows(IllegalArgumentException.class, () -> BillingRules.month("2026-13"));
    }
    @Test void aMonthCanBeBilledOnlyOnceItIsOver() {
        assertDoesNotThrow(() -> BillingRules.requireEnded(SEPT, d("2026-10-01")));
        assertThrows(IllegalArgumentException.class, () -> BillingRules.requireEnded(SEPT, d("2026-09-30")));
        assertThrows(IllegalArgumentException.class, () -> BillingRules.requireEnded(SEPT, d("2026-09-15")));
        assertThrows(IllegalArgumentException.class, () -> BillingRules.requireEnded(YearMonth.of(2026, 10), d("2026-10-15")));
    }
    @Test void aMachineOnHireAllMonthIsBilledForTheWholeMonth() {
        Span s = BillingRules.span(SEPT, d("2026-08-10"), null);
        assertEquals(d("2026-09-01"), s.from());
        assertEquals(d("2026-09-30"), s.to());
    }
    @Test void aMachineThatArrivedOrLeftMidMonthIsBilledForThoseDaysOnly() {
        Span arrived = BillingRules.span(SEPT, d("2026-09-12"), null);
        assertEquals(d("2026-09-12"), arrived.from());
        assertEquals(d("2026-09-30"), arrived.to());
        Span left = BillingRules.span(SEPT, d("2026-08-01"), d("2026-09-20"));
        assertEquals(d("2026-09-01"), left.from());
        assertEquals(d("2026-09-20"), left.to());
        Span both = BillingRules.span(SEPT, d("2026-09-05"), d("2026-09-05"));
        assertEquals(both.from(), both.to());
    }
    @Test void aMachineNotOnHireInTheMonthHasNoSpan() {
        assertNull(BillingRules.span(SEPT, d("2026-10-01"), null));
        assertNull(BillingRules.span(SEPT, d("2026-07-01"), d("2026-08-31")));
    }
    @Test void coverSaysNoneSomeOrAll() {
        Span month = new Span(d("2026-09-01"), d("2026-09-30"));
        assertEquals(Cover.NONE, BillingRules.cover(month, List.of()));
        assertEquals(Cover.NONE, BillingRules.cover(month, List.of(new Span(d("2026-08-01"), d("2026-08-31")))));
        assertEquals(Cover.PARTIAL, BillingRules.cover(month, List.of(new Span(d("2026-09-01"), d("2026-09-10")))));
        assertEquals(Cover.FULL, BillingRules.cover(month, List.of(new Span(d("2026-08-20"), d("2026-09-30")))));
        assertEquals(Cover.FULL, BillingRules.cover(month, List.of(new Span(d("2026-09-01"), d("2026-09-15")), new Span(d("2026-09-16"), d("2026-09-30")))));
        assertEquals(Cover.PARTIAL, BillingRules.cover(month, List.of(new Span(d("2026-09-01"), d("2026-09-14")), new Span(d("2026-09-16"), d("2026-09-30")))));
    }
    @Test void theInvoiceTitleNamesTheMonth() {
        assertEquals("Plant hire September 2026", BillingRules.title(SEPT));
        assertEquals("Plant hire January 2027", BillingRules.title(YearMonth.of(2027, 1)));
    }
}
