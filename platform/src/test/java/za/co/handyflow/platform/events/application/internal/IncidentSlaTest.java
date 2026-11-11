package za.co.handyflow.platform.events.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.events.application.internal.IncidentSla.Assessment;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The overdue flags are the only alert an incident gets, so their boundaries have to be exactly right. */
class IncidentSlaTest {

    private static final Instant CREATED = Instant.parse("2026-11-05T10:00:00Z");

    private static Instant after(long minutes) {
        return CREATED.plusSeconds(minutes * 60);
    }

    @Test
    void theTargets_getLooserAsSeverityDrops() {
        assertThat(IncidentSla.acknowledgeWithinMinutes("CRITICAL")).isEqualTo(5);
        assertThat(IncidentSla.acknowledgeWithinMinutes("HIGH")).isEqualTo(15);
        assertThat(IncidentSla.acknowledgeWithinMinutes("MEDIUM")).isEqualTo(60);
        assertThat(IncidentSla.acknowledgeWithinMinutes("LOW")).isEqualTo(240);

        assertThat(IncidentSla.resolveWithinMinutes("CRITICAL")).isEqualTo(60);
        assertThat(IncidentSla.resolveWithinMinutes("HIGH")).isEqualTo(180);
        assertThat(IncidentSla.resolveWithinMinutes("MEDIUM")).isEqualTo(480);
        assertThat(IncidentSla.resolveWithinMinutes("LOW")).isEqualTo(1440);
    }

    @Test
    void aFreshIncident_isNotOverdue() {
        Assessment a = IncidentSla.assess("CRITICAL", "OPEN", CREATED, null, null, after(1));
        assertThat(a.acknowledgementOverdue()).isFalse();
        assertThat(a.resolutionOverdue()).isFalse();
        assertThat(a.minutesOpen()).isEqualTo(1);
    }

    @Test
    void acknowledgement_isOverdueOnlyOncePastTheTarget_notAtIt() {
        assertThat(IncidentSla.assess("CRITICAL", "OPEN", CREATED, null, null, after(5)).acknowledgementOverdue()).isFalse();
        assertThat(IncidentSla.assess("CRITICAL", "OPEN", CREATED, null, null, after(6)).acknowledgementOverdue()).isTrue();
    }

    @Test
    void anAcknowledgedIncident_isNeverAcknowledgementOverdue_howeverLongAgo() {
        Assessment a = IncidentSla.assess("CRITICAL", "IN_PROGRESS", CREATED, after(2), null, after(45));
        assertThat(a.acknowledgementOverdue()).isFalse();
        assertThat(a.resolutionOverdue()).isFalse();   // 45 < 60
    }

    @Test
    void resolution_isOverdueOnceThePastTheTarget_evenIfAcknowledged() {
        assertThat(IncidentSla.assess("CRITICAL", "IN_PROGRESS", CREATED, after(1), null, after(60)).resolutionOverdue()).isFalse();
        assertThat(IncidentSla.assess("CRITICAL", "IN_PROGRESS", CREATED, after(1), null, after(61)).resolutionOverdue()).isTrue();
    }

    @Test
    void anEscalatedButUnacknowledgedIncident_stillCountsAsUnacknowledged() {
        assertThat(IncidentSla.assess("HIGH", "ESCALATED", CREATED, null, null, after(20)).acknowledgementOverdue()).isTrue();
    }

    @Test
    void aResolvedOrClosedIncident_isNeverOverdue_andReportsHowLongItTook() {
        Assessment resolved = IncidentSla.assess("CRITICAL", "RESOLVED", CREATED, after(2), after(300), after(1000));
        assertThat(resolved.acknowledgementOverdue()).isFalse();
        assertThat(resolved.resolutionOverdue()).isFalse();
        assertThat(resolved.minutesOpen()).isEqualTo(300);     // how long it took, not how long ago it was

        Assessment closed = IncidentSla.assess("CRITICAL", "CLOSED", CREATED, after(2), after(90), after(5000));
        assertThat(closed.resolutionOverdue()).isFalse();
        assertThat(closed.minutesOpen()).isEqualTo(90);
    }

    @Test
    void aClockThatIsBehindTheCreationTime_neverProducesNegativeMinutes() {
        assertThat(IncidentSla.assess("HIGH", "OPEN", CREATED, null, null, CREATED.minusSeconds(120)).minutesOpen()).isZero();
    }
}
