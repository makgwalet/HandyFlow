package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.GuardScoreCalculator.Facts;

import static org.assertj.core.api.Assertions.assertThat;

/** The operational score: weights, missing data, bands and the explanation of each component. */
class GuardScoreCalculatorTest {

    private static Facts facts(int done, int pulled, int missed, int late, int pReq, int pMet, int low, int med, int high, int crit,
                               int subst, Integer ready, int ratings, double avg) {
        return new Facts(done, pulled, missed, late, pReq, pMet, low, med, high, crit, subst, ready, ratings, avg);
    }

    @Test @DisplayName("A strong guard with full evidence scores excellent and every component has data")
    void strong() {
        var r = GuardScoreCalculator.calculate(facts(20, 1, 1, 2, 10, 9, 0, 1, 0, 0, 0, 100, 4, 4.5));
        assertThat(r.coverage()).isEqualTo(100);
        assertThat(r.score()).isBetween(85, 95);
        assertThat(r.band()).isEqualTo("EXCELLENT");
        assertThat(r.components()).hasSize(7).allMatch(GuardScoreCalculator.Component::hasData);
        assertThat(r.components().stream().mapToInt(GuardScoreCalculator.Component::weight).sum()).isEqualTo(100);
    }

    @Test @DisplayName("No records at all gives no score rather than a zero")
    void noData() {
        var r = GuardScoreCalculator.calculate(facts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0, 0));
        assertThat(r.score()).isNull();
        assertThat(r.band()).isEqualTo("NOT_ENOUGH_DATA");
        assertThat(r.coverage()).isZero();
    }

    @Test @DisplayName("A new guard with one shift and a readiness figure is not scored")
    void newGuard() {
        var r = GuardScoreCalculator.calculate(facts(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 80, 0, 0));
        assertThat(r.score()).isNull();
        assertThat(r.coverage()).isEqualTo(15);
    }

    @Test @DisplayName("Readiness and ratings alone (30 points) are below the minimum coverage")
    void thin() {
        assertThat(GuardScoreCalculator.calculate(facts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 100, 3, 5)).score()).isNull();
    }

    @Test @DisplayName("Missing components are left out and the score is taken over the evidence that exists")
    void partial() {
        var r = GuardScoreCalculator.calculate(facts(3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 100, 1, 5));
        assertThat(r.coverage()).isEqualTo(85);
        assertThat(r.score()).isEqualTo(100);
        assertThat(r.components().stream().filter(c -> !c.hasData()).map(GuardScoreCalculator.Component::key)).containsExactly("PATROL");
    }

    @Test @DisplayName("Missed shifts, lateness, a critical incident and substantiated complaints put a guard at risk")
    void atRisk() {
        var r = GuardScoreCalculator.calculate(facts(2, 0, 4, 5, 0, 0, 0, 0, 0, 1, 3, 40, 1, 1.5));
        assertThat(r.band()).isEqualTo("AT_RISK");
        assertThat(r.score()).isLessThan(50);
    }

    @Test @DisplayName("Late arrivals cannot take punctuality below zero")
    void lateCapped() {
        var r = GuardScoreCalculator.calculate(facts(5, 0, 0, 9, 0, 0, 0, 0, 0, 0, 0, 100, 1, 5));
        assertThat(r.components().get(1).fraction()).isZero();
    }

    @Test @DisplayName("Each component explains itself")
    void explains() {
        var r = GuardScoreCalculator.calculate(facts(8, 0, 2, 1, 4, 3, 0, 0, 1, 0, 1, 90, 2, 4.0));
        assertThat(r.components().get(0).detail()).isEqualTo("8 of 10 shifts worked");
        assertThat(r.components().get(2).detail()).isEqualTo("3 of 4 shifts met the required scans");
        assertThat(r.components().get(4).detail()).isEqualTo("1 substantiated");
        assertThat(r.components().get(6).detail()).isEqualTo("4.0 out of 5 across 2 ratings");
        assertThat(GuardScoreCalculator.calculate(facts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0, 0)).components().get(0).detail()).startsWith("Not enough data");
    }

    @Test @DisplayName("Band boundaries")
    void bands() {
        assertThat(GuardScoreCalculator.band(85)).isEqualTo("EXCELLENT");
        assertThat(GuardScoreCalculator.band(84)).isEqualTo("GOOD");
        assertThat(GuardScoreCalculator.band(70)).isEqualTo("GOOD");
        assertThat(GuardScoreCalculator.band(69)).isEqualTo("NEEDS_ATTENTION");
        assertThat(GuardScoreCalculator.band(50)).isEqualTo("NEEDS_ATTENTION");
        assertThat(GuardScoreCalculator.band(49)).isEqualTo("AT_RISK");
    }
}
