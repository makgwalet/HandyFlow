package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.dto.TimelineEvent;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TimelineAssemblerTest {

    private static TimelineEvent e(String kind, String at) {
        return new TimelineEvent(kind, UUID.randomUUID(), at == null ? null : Instant.parse(at), kind, null, null);
    }

    private final List<TimelineEvent> all = List.of(
            e("APPOINTMENT", "2026-01-01T10:00:00Z"), e("CONSULTATION", "2026-03-01T10:00:00Z"),
            e("PAYMENT", "2026-02-01T10:00:00Z"), e("LAB", "2026-04-01T10:00:00Z"),
            e("CLAIM", "2026-05-01T10:00:00Z"), e("LAB", null));

    @Test
    @DisplayName("newest first, and events without a time are left out")
    void ordering() {
        var r = TimelineAssembler.select(all, null, null, null, true, null);
        assertThat(r).extracting(TimelineEvent::kind).containsExactly("CLAIM", "LAB", "CONSULTATION", "PAYMENT", "APPOINTMENT");
    }

    @Test
    @DisplayName("claims and payments are hidden without billing access, even when asked for by name")
    void billingHidden() {
        assertThat(TimelineAssembler.select(all, null, null, null, false, null))
                .extracting(TimelineEvent::kind).containsExactly("LAB", "CONSULTATION", "APPOINTMENT");
        assertThat(TimelineAssembler.select(all, Set.of("PAYMENT"), null, null, false, null)).isEmpty();
    }

    @Test
    @DisplayName("kind, date range (from inclusive, to exclusive) and limit")
    void filters() {
        assertThat(TimelineAssembler.select(all, Set.of("LAB"), null, null, true, null)).hasSize(1);
        assertThat(TimelineAssembler.select(all, null, Instant.parse("2026-02-01T10:00:00Z"),
                Instant.parse("2026-04-01T10:00:00Z"), true, null)).hasSize(2);
        assertThat(TimelineAssembler.select(all, null, null, null, true, 2)).hasSize(2);
        assertThat(TimelineAssembler.select(all, null, null, null, true, 100_000)).hasSize(5);
        assertThat(TimelineAssembler.select(all, Set.of(), null, null, true, null)).hasSize(5);
    }
}
