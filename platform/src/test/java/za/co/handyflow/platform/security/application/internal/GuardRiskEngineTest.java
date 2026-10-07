package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.GuardRiskEngine.Facts;
import za.co.handyflow.platform.security.application.internal.GuardRiskEngine.Recommendation;
import za.co.handyflow.platform.security.application.internal.GuardRiskEngine.Settings;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The risk engine recommends for a person to review; thresholds come from the settings. */
class GuardRiskEngineTest {

    private final Settings d = Settings.defaults();
    private static List<String> codes(List<Recommendation> r) { return r.stream().map(Recommendation::code).toList(); }

    @Test @DisplayName("Nothing to recommend when there is nothing on record")
    void none() { assertThat(GuardRiskEngine.evaluate(new Facts(0, 0, 0, 0), d)).isEmpty(); }

    @Test @DisplayName("The default ladder: 1 supervisor review, 3 warning review, 5 formal investigation, and only the highest shows")
    void ladder() {
        assertThat(codes(GuardRiskEngine.evaluate(new Facts(1, 0, 0, 0), d))).containsExactly("SUPERVISOR_REVIEW");
        assertThat(codes(GuardRiskEngine.evaluate(new Facts(2, 0, 0, 0), d))).containsExactly("SUPERVISOR_REVIEW");
        assertThat(codes(GuardRiskEngine.evaluate(new Facts(3, 0, 0, 0), d))).containsExactly("WARNING_REVIEW");
        assertThat(codes(GuardRiskEngine.evaluate(new Facts(5, 0, 0, 0), d))).containsExactly("FORMAL_INVESTIGATION");
        assertThat(codes(GuardRiskEngine.evaluate(new Facts(9, 0, 0, 0), d))).containsExactly("FORMAL_INVESTIGATION");
    }

    @Test @DisplayName("Repeated confirmed misconduct, a critical incident and an urgent open complaint each add a recommendation")
    void others() {
        assertThat(codes(GuardRiskEngine.evaluate(new Facts(1, 2, 1, 1), d)))
                .containsExactly("SUPERVISOR_REVIEW", "DISCIPLINARY_REVIEW", "SUSPENSION_REVIEW", "URGENT_COMPLAINT");
        assertThat(codes(GuardRiskEngine.evaluate(new Facts(0, 1, 0, 0), d))).isEmpty();
    }

    @Test @DisplayName("The critical-incident rule can be switched off")
    void criticalOff() {
        assertThat(GuardRiskEngine.evaluate(new Facts(0, 0, 2, 0), new Settings(1, 3, 5, 90, 2, 365, false))).isEmpty();
    }

    @Test @DisplayName("Custom thresholds are honoured and the reason quotes them")
    void custom() {
        var r = GuardRiskEngine.evaluate(new Facts(2, 0, 0, 0), new Settings(1, 2, 4, 60, 2, 365, true));
        assertThat(r).hasSize(1);
        assertThat(r.get(0).code()).isEqualTo("WARNING_REVIEW");
        assertThat(r.get(0).reason()).contains("2 complaints in the last 60 days").contains("threshold 2");
    }

    @Test @DisplayName("Recommendations never claim a decision has been made")
    void wording() {
        for (Recommendation r : GuardRiskEngine.evaluate(new Facts(5, 2, 1, 1), d))
            assertThat((r.title() + " " + r.reason()).toLowerCase()).doesNotContain("dismiss").doesNotContain("has been suspended").doesNotContain("terminate");
    }

    @Test @DisplayName("Settings must rise, stay in range and be at least 1")
    void validation() {
        assertThat(GuardRiskEngine.validate(d)).isNull();
        assertThat(GuardRiskEngine.validate(new Settings(3, 2, 5, 90, 2, 365, true))).contains("rise");
        assertThat(GuardRiskEngine.validate(new Settings(1, 3, 2, 90, 2, 365, true))).contains("rise");
        assertThat(GuardRiskEngine.validate(new Settings(0, 3, 5, 90, 2, 365, true))).contains("at least 1");
        assertThat(GuardRiskEngine.validate(new Settings(1, 3, 5, 3, 2, 365, true))).contains("window");
        assertThat(GuardRiskEngine.validate(new Settings(1, 3, 5, 90, 2, 10, true))).contains("window");
    }
}
