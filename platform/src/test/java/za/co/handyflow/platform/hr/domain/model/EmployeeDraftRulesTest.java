package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeDraftRulesTest {

    @Test
    void acceptsAJsonObjectOnAStepFromZeroToFive() {
        EmployeeDraftRules.check("{\"firstName\":\"A\"}", 0);
        EmployeeDraftRules.check(" {\"x\":1}", 5);
    }

    @Test
    void refusesEmptyOversizeOrNonJsonPayloadsAndBadSteps() {
        assertThatThrownBy(() -> EmployeeDraftRules.check(" ", 0)).hasMessageContaining("nothing to save");
        assertThatThrownBy(() -> EmployeeDraftRules.check("[1]", 0)).hasMessageContaining("not in the form");
        assertThatThrownBy(() -> EmployeeDraftRules.check("{" + "x".repeat(EmployeeDraftRules.MAX_PAYLOAD_CHARS), 0)).hasMessageContaining("too large");
        assertThatThrownBy(() -> EmployeeDraftRules.check("{}", 6)).hasMessageContaining("0 to 5");
    }

    @Test
    void labelsADraftByNameOrAsUnnamed() {
        assertThat(EmployeeDraftRules.label(" Thandi ", "Nkosi")).isEqualTo("Thandi Nkosi");
        assertThat(EmployeeDraftRules.label(null, " ")).isEqualTo("Unnamed employee");
    }

    @Test
    void aDraftExpiresAfterThirtyDays() {
        Instant now = Instant.parse("2026-11-04T10:00:00Z");
        assertThat(EmployeeDraftRules.expired(now.minus(29, ChronoUnit.DAYS), now)).isFalse();
        assertThat(EmployeeDraftRules.expired(now.minus(31, ChronoUnit.DAYS), now)).isTrue();
    }
}
