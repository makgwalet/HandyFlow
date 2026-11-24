package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test @DisplayName("only the planned moves are allowed")
    void moves() {
        assertThat(CaseRules.canMove("OPEN", "IN_PROGRESS")).isTrue();
        assertThat(CaseRules.canMove("IN_PROGRESS", "AWAITING_CLIENT")).isTrue();
        assertThat(CaseRules.canMove("RESOLVED", "CLOSED")).isTrue();
        assertThat(CaseRules.canMove("RESOLVED", "IN_PROGRESS")).isTrue();
        assertThat(CaseRules.canMove("OPEN", "RESOLVED")).isFalse();
        assertThat(CaseRules.canMove("CLOSED", "IN_PROGRESS")).isFalse();
        assertThat(CaseRules.canMove("CANCELLED", "OPEN")).isFalse();
        assertThat(CaseRules.canMove("RESOLVED", "CANCELLED")).isFalse();
    }

    @Test @DisplayName("live means not yet closed or cancelled")
    void live() {
        assertThat(CaseRules.isLive("OPEN")).isTrue();
        assertThat(CaseRules.isLive("AWAITING_CLIENT")).isTrue();
        assertThat(CaseRules.isLive("CLOSED")).isFalse();
        assertThat(CaseRules.isLive("CANCELLED")).isFalse();
    }

    @Test @DisplayName("titles, steps and reasons are checked")
    void text() {
        assertThat(CaseRules.title("  Fix POPIA officer ")).isEqualTo("Fix POPIA officer");
        assertThatThrownBy(() -> CaseRules.title("ab")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CaseRules.step(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CaseRules.reason("too short", "why")).hasMessageContaining("15");
        assertThat(CaseRules.reason("The client no longer trades", "why")).isEqualTo("The client no longer trades");
    }

    @Test @DisplayName("severity defaults, is upper-cased and rejects unknown values; risk maps across")
    void severity() {
        assertThat(CaseRules.severity(null, "MEDIUM")).isEqualTo("MEDIUM");
        assertThat(CaseRules.severity("high", "MEDIUM")).isEqualTo("HIGH");
        assertThatThrownBy(() -> CaseRules.severity("URGENT", "MEDIUM")).isInstanceOf(IllegalArgumentException.class);
        assertThat(CaseRules.severityFromRisk("CRITICAL")).isEqualTo("CRITICAL");
        assertThat(CaseRules.severityFromRisk(null)).isEqualTo("MEDIUM");
        assertThat(CaseRules.severityFromRisk("odd")).isEqualTo("MEDIUM");
    }

    @Test @DisplayName("a due date in the past is refused, none is fine")
    void due() {
        CaseRules.dueDate(null, TODAY);
        CaseRules.dueDate(TODAY, TODAY);
        assertThatThrownBy(() -> CaseRules.dueDate(TODAY.minusDays(1), TODAY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("resolving needs no open steps and a real note")
    void resolve() {
        assertThat(CaseRules.resolveBlockers(0, "Appointed an information officer and registered")).isEmpty();
        assertThat(CaseRules.resolveBlockers(2, "Appointed an information officer")).hasSize(1);
        assertThat(CaseRules.resolveBlockers(2, "Appointed an information officer").get(0)).contains("2 steps are");
        assertThat(CaseRules.resolveBlockers(1, "short")).hasSize(2);
    }

    @Test @DisplayName("a second person closes freely; the same person needs a reason")
    void close() {
        assertThat(CaseRules.closeException(A, B, null)).isNull();
        assertThatThrownBy(() -> CaseRules.closeException(A, A, null)).hasMessageContaining("someone else");
        assertThatThrownBy(() -> CaseRules.closeException(A, A, "no")).isInstanceOf(IllegalArgumentException.class);
        assertThat(CaseRules.closeException(A, A, "Sole practitioner, nobody else to check")).isEqualTo("Sole practitioner, nobody else to check");
    }
}
