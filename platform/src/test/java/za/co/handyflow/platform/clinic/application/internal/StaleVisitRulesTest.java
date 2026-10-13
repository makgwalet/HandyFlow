package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.StaleVisitRules.Outcome;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StaleVisitRulesTest {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    // 13 Oct 2026 00:30 SAST = 12 Oct 22:30 UTC: "today" in the clinic has started although UTC is still on the 12th.
    static final Instant NOW = Instant.parse("2026-10-12T22:30:00Z");
    static final Instant TODAY = StaleVisitRules.startOfToday(NOW, SAST);
    static final Instant YESTERDAY_9AM = Instant.parse("2026-10-12T07:00:00Z");
    static final Instant TODAY_9AM = Instant.parse("2026-10-13T07:00:00Z");

    @Test void todayStartsAtClinicMidnight() {
        assertThat(TODAY).isEqualTo(Instant.parse("2026-10-12T22:00:00Z"));
    }

    @Test void aPastVisitWithASignedConsultationIsCompleted() {
        assertThat(StaleVisitRules.decide("CHECKED_IN", YESTERDAY_9AM, TODAY, List.of("SIGNED"))).isEqualTo(Outcome.COMPLETE);
        assertThat(StaleVisitRules.decide("IN_PROGRESS", YESTERDAY_9AM, TODAY, List.of("ABANDONED", "LOCKED"))).isEqualTo(Outcome.COMPLETE);
    }

    @Test void aPastVisitWithUnfinishedClinicalWorkIsLeftOpen() {
        assertThat(StaleVisitRules.decide("IN_PROGRESS", YESTERDAY_9AM, TODAY, List.of("DRAFT"))).isEqualTo(Outcome.KEEP);
        assertThat(StaleVisitRules.decide("TRIAGED", YESTERDAY_9AM, TODAY, List.of("READY_FOR_DOCTOR"))).isEqualTo(Outcome.KEEP);
    }

    @Test void aPastVisitWithNoConsultationIsNotSeen() {
        assertThat(StaleVisitRules.decide("CHECKED_IN", YESTERDAY_9AM, TODAY, List.of())).isEqualTo(Outcome.NOT_SEEN);
        assertThat(StaleVisitRules.decide("IN_PROGRESS", YESTERDAY_9AM, TODAY, List.of("ABANDONED"))).isEqualTo(Outcome.NOT_SEEN);
    }

    @Test void todaysVisitsAndOtherStatusesAreLeftAlone() {
        assertThat(StaleVisitRules.decide("CHECKED_IN", TODAY_9AM, TODAY, List.of())).isEqualTo(Outcome.KEEP);
        assertThat(StaleVisitRules.decide("SCHEDULED", YESTERDAY_9AM, TODAY, List.of())).isEqualTo(Outcome.KEEP);
        assertThat(StaleVisitRules.decide("COMPLETED", YESTERDAY_9AM, TODAY, List.of())).isEqualTo(Outcome.KEEP);
        assertThat(StaleVisitRules.decide(null, YESTERDAY_9AM, TODAY, List.of())).isEqualTo(Outcome.KEEP);
    }
}
