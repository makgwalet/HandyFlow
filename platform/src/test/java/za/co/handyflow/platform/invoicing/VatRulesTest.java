package za.co.handyflow.platform.invoicing;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.invoicing.domain.model.VatRules;
import za.co.handyflow.platform.invoicing.domain.model.VatRules.Row;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VatRulesTest {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    static final BigDecimal PLATFORM = new BigDecimal("15.00");
    static BigDecimal n(String s) { return new BigDecimal(s); }
    static Row row(boolean registered, String rate, LocalDate from) { return new Row(UUID.randomUUID(), registered, n(rate), from); }

    @Test void withNoSettingACompanyIsRegisteredAtThePlatformRate() {
        VatRules.Treatment t = VatRules.on(List.of(), TODAY, PLATFORM);
        assertTrue(t.registered());
        assertEquals(PLATFORM, t.ratePercent());
        assertFalse(t.configured());
    }

    @Test void theSettingOnADayIsTheLatestChangeThatHasStarted() {
        List<Row> rows = List.of(row(true, "15.00", LocalDate.of(2026, 1, 1)), row(false, "0.00", LocalDate.of(2026, 11, 1)));
        assertEquals(n("15.00"), VatRules.on(rows, TODAY, PLATFORM).ratePercent());
        assertTrue(VatRules.on(rows, TODAY, PLATFORM).configured());
        assertFalse(VatRules.on(rows, LocalDate.of(2026, 11, 1), PLATFORM).registered());
        assertEquals(n("0.00"), VatRules.on(rows, LocalDate.of(2026, 12, 1), PLATFORM).ratePercent());
        // before the first change there is no setting, so the default applies
        assertFalse(VatRules.on(rows, LocalDate.of(2025, 12, 31), PLATFORM).configured());
    }

    @Test void aChangeCannotStartInThePastOrTooFarAhead() {
        assertThrows(IllegalArgumentException.class, () -> VatRules.check(true, n("15"), TODAY.minusDays(1), TODAY, true, List.of(), PLATFORM));
        assertThrows(IllegalArgumentException.class, () -> VatRules.check(true, n("15"), TODAY.plusYears(1).plusDays(1), TODAY, true, List.of(), PLATFORM));
        assertThrows(IllegalArgumentException.class, () -> VatRules.check(true, n("15"), null, TODAY, true, List.of(), PLATFORM));
        assertEquals(n("15.00"), VatRules.check(true, n("15"), TODAY, TODAY, true, List.of(), PLATFORM));
    }

    @Test void registeringNeedsAVatNumberAndASensibleRate() {
        assertThrows(IllegalStateException.class, () -> VatRules.check(true, n("15"), TODAY, TODAY, false, List.of(), PLATFORM));
        assertThrows(IllegalArgumentException.class, () -> VatRules.check(true, n("0"), TODAY, TODAY, true, List.of(), PLATFORM));
        assertThrows(IllegalArgumentException.class, () -> VatRules.check(true, null, TODAY, TODAY, true, List.of(), PLATFORM));
        assertThrows(IllegalArgumentException.class, () -> VatRules.check(true, n("101"), TODAY, TODAY, true, List.of(), PLATFORM));
        assertEquals(n("15.50"), VatRules.check(true, n("15.5"), TODAY, TODAY, true, List.of(), PLATFORM));
    }

    @Test void notBeingRegisteredStoresZeroWhateverRateWasSentAndNeedsNoVatNumber() {
        assertEquals(n("0.00"), VatRules.check(false, n("15"), TODAY, TODAY, false, List.of(), PLATFORM));
    }

    @Test void twoChangesCannotStartOnTheSameDayAndAnUnchangedTreatmentIsRefused() {
        List<Row> rows = List.of(row(true, "15.00", TODAY.plusDays(10)));
        assertThrows(IllegalStateException.class, () -> VatRules.check(false, null, TODAY.plusDays(10), TODAY, true, rows, PLATFORM));
        assertThrows(IllegalArgumentException.class, () -> VatRules.check(true, n("15"), TODAY.plusDays(20), TODAY, true, rows, PLATFORM));
        assertDoesNotThrow(() -> VatRules.check(false, null, TODAY.plusDays(20), TODAY, true, rows, PLATFORM));
    }

    @Test void onlyAChangeThatHasNotStartedCanBeCancelled() {
        assertDoesNotThrow(() -> VatRules.checkCancel(TODAY.plusDays(1), TODAY));
        assertThrows(IllegalStateException.class, () -> VatRules.checkCancel(TODAY, TODAY));
        assertThrows(IllegalStateException.class, () -> VatRules.checkCancel(TODAY.minusDays(30), TODAY));
    }
}
