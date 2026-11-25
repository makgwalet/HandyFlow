package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PERSON = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    private ComplianceTimeEntry entry(int minutes, LocalDate day, boolean admin, int already) {
        return ComplianceTimeEntry.record(TENANT, CLIENT, PERSON, day, minutes, " CS-REVIEW ", null, true, " note ", TODAY, admin, already);
    }

    @Test
    void goodEntryIsTidied() {
        ComplianceTimeEntry e = entry(90, TODAY, false, 0);
        assertThat(e.getServiceCode().equals("CS-REVIEW")).isTrue();
        assertThat(e.getKind().equals("PLANNED")).isTrue();
        assertThat(e.getNote().equals("note")).isTrue();
        assertThat(e.isActive()).isTrue();
    }

    @Test
    void entrySizeLimits() {
        assertThatThrownBy(() -> entry(0, TODAY, false, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry(721, TODAY, false, 0)).isInstanceOf(IllegalArgumentException.class);
        entry(720, TODAY, false, 0);
    }

    @Test
    void dayCapIsSixteenHours() {
        entry(60, TODAY, false, 900);
        assertThatThrownBy(() -> entry(61, TODAY, false, 900)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noFutureAndLimitedBackdating() {
        assertThatThrownBy(() -> entry(30, TODAY.plusDays(1), false, 0)).isInstanceOf(IllegalArgumentException.class);
        entry(30, TODAY.minusDays(60), false, 0);
        assertThatThrownBy(() -> entry(30, TODAY.minusDays(61), false, 0)).isInstanceOf(IllegalArgumentException.class);
        entry(30, TODAY.minusDays(200), true, 0);
        assertThatThrownBy(() -> entry(30, null, false, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kindMustBeKnown() {
        assertThat(TimeRules.kind("rework").equals("REWORK")).isTrue();
        assertThatThrownBy(() -> TimeRules.kind("holiday")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void voidingNeedsAReasonAndHappensOnce() {
        ComplianceTimeEntry e = entry(30, TODAY, false, 0);
        assertThatThrownBy(() -> e.voidIt(PERSON, "oops")).isInstanceOf(IllegalArgumentException.class);
        e.voidIt(PERSON, "Entered against the wrong client");
        assertThat(e.isActive()).isFalse();
        assertThatThrownBy(() -> e.voidIt(PERSON, "Entered against the wrong client")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void totalsSplitBillableReworkAndUnplanned() {
        TimeRules.Totals t = TimeRules.Totals.none().plus(60, true, "PLANNED").plus(30, false, "REWORK").plus(15, true, "UNPLANNED");
        assertThat(t.minutes() == 105).isTrue();
        assertThat(t.billableMinutes() == 75).isTrue();
        assertThat(t.reworkMinutes() == 30).isTrue();
        assertThat(t.unplannedMinutes() == 15).isTrue();
        assertThat(t.add(t).minutes() == 210).isTrue();
    }

    @Test
    void hoursReadNaturally() {
        assertThat(TimeRules.hours(90).equals("1h 30m")).isTrue();
        assertThat(TimeRules.hours(45).equals("45m")).isTrue();
        assertThat(TimeRules.hours(120).equals("2h")).isTrue();
    }
}
