package za.co.handyflow.platform.security.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.IncidentWorkflow.Action;

import static org.assertj.core.api.Assertions.assertThat;

/** What may be done to an incident in each status, and how escalation is checked. */
class IncidentWorkflowTest {

    @Test @DisplayName("An open incident can be acknowledged, resolved, assigned, annotated, evidenced and escalated")
    void open() {
        assertThat(IncidentWorkflow.allowed("OPEN", "LOW")).containsExactlyInAnyOrder(
                Action.ACKNOWLEDGE, Action.RESOLVE, Action.ASSIGN, Action.NOTE, Action.EVIDENCE, Action.ESCALATE);
    }

    @Test @DisplayName("An acknowledged incident cannot be acknowledged again")
    void acknowledged() {
        assertThat(IncidentWorkflow.allowed("ACKNOWLEDGED", "HIGH")).doesNotContain(Action.ACKNOWLEDGE, Action.REOPEN)
                .contains(Action.RESOLVE, Action.ESCALATE);
    }

    @Test @DisplayName("A resolved incident can only be reopened or annotated")
    void resolved() {
        assertThat(IncidentWorkflow.allowed("RESOLVED", "MEDIUM")).containsExactlyInAnyOrder(Action.REOPEN, Action.NOTE);
    }

    @Test @DisplayName("A critical incident cannot be escalated further")
    void critical() {
        assertThat(IncidentWorkflow.allowed("OPEN", "CRITICAL")).doesNotContain(Action.ESCALATE);
        assertThat(IncidentWorkflow.nextSeverity("CRITICAL")).isNull();
    }

    @Test @DisplayName("Escalation moves up one level by default, or to a higher named level, never down or sideways")
    void escalation() {
        assertThat(IncidentWorkflow.nextSeverity("LOW")).isEqualTo("MEDIUM");
        assertThat(IncidentWorkflow.nextSeverity("high")).isEqualTo("CRITICAL");
        assertThat(IncidentWorkflow.escalationError("LOW", null)).isNull();
        assertThat(IncidentWorkflow.escalationError("LOW", "critical")).isNull();
        assertThat(IncidentWorkflow.escalationError("HIGH", "MEDIUM")).contains("raise");
        assertThat(IncidentWorkflow.escalationError("HIGH", "HIGH")).contains("raise");
        assertThat(IncidentWorkflow.escalationError("LOW", "SEVERE")).contains("Unknown");
        assertThat(IncidentWorkflow.escalationError("CRITICAL", null)).contains("highest");
    }

    @Test @DisplayName("Unknown statuses allow nothing")
    void unknown() { assertThat(IncidentWorkflow.allowed("???", "LOW")).isEmpty(); }
}
