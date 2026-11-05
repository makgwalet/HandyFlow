package za.co.handyflow.platform.config;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.config.StartupProfile.Step;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartupProfileTest {

    private static Step step(long id, Long parent, String name, String label, long millis) {
        return new Step(id, parent, name, label, Duration.ofMillis(millis));
    }

    @Test
    void ownTimeIsTheDurationMinusTheChildren() {
        List<Step> steps = List.of(
                step(1, null, "spring.context.refresh", null, 10_000),
                step(2, 1L, "spring.beans.instantiate", "slowBean", 7_000),
                step(3, 2L, "spring.beans.instantiate", "dependency", 6_000));
        var rows = StartupProfile.rank(steps, 10);
        assertThat(rows.get(0).label()).isEqualTo("dependency");
        assertThat(rows.get(0).own()).isEqualTo(Duration.ofMillis(6_000));
        assertThat(rows.get(1).label()).isEqualTo("slowBean");
        assertThat(rows.get(1).own()).isEqualTo(Duration.ofMillis(1_000));
        assertThat(rows.get(2).own()).isEqualTo(Duration.ofMillis(3_000));
    }

    @Test
    void theRankingIsLimited() {
        List<Step> steps = List.of(step(1, null, "a", "x", 5), step(2, null, "b", "y", 9), step(3, null, "c", "z", 7));
        var rows = StartupProfile.rank(steps, 2);
        assertThat(rows).extracting(StartupProfile.Row::name).containsExactly("b", "c");
    }

    @Test
    void timeIsAddedUpPerKind() {
        List<Step> steps = List.of(
                step(1, null, "spring.beans.instantiate", "a", 2_000),
                step(2, null, "spring.beans.instantiate", "b", 3_000),
                step(3, null, "spring.data.repository.scanning", null, 1_000));
        var byKind = StartupProfile.byKind(steps);
        assertThat(byKind.keySet()).containsExactly("spring.beans.instantiate", "spring.data.repository.scanning");
        assertThat(byKind.get("spring.beans.instantiate")).isEqualTo(Duration.ofSeconds(5));
    }
}
