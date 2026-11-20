package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FleetSignalRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    @Test @DisplayName("Past dates are expired; today and the next 30 days are expiring; later and missing dates count for nothing")
    void boundaries() {
        var v = new FleetSignalRules.Dates(TODAY.minusDays(1), TODAY, TODAY.plusDays(30));
        var w = new FleetSignalRules.Dates(TODAY.plusDays(31), null, null);
        FleetSignalRules.Counts c = FleetSignalRules.count(List.of(v, w), TODAY);
        assertEquals(1, c.expired());
        assertEquals(2, c.expiring());
    }

    @Test @DisplayName("Each of the three dates on each vehicle counts separately; no vehicles gives zeros")
    void perDate() {
        var lapsed = new FleetSignalRules.Dates(TODAY.minusDays(5), TODAY.minusDays(40), TODAY.minusDays(1));
        assertEquals(6, FleetSignalRules.count(List.of(lapsed, lapsed), TODAY).expired());
        FleetSignalRules.Counts none = FleetSignalRules.count(List.of(), TODAY);
        assertEquals(0, none.expired());
        assertEquals(0, none.expiring());
    }
}
