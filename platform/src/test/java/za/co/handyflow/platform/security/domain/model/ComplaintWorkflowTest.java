package za.co.handyflow.platform.security.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.ComplaintWorkflow.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The complaint rules: which step may follow which, what counts as urgent, which actions fit which finding. */
class ComplaintWorkflowTest {

    @Test @DisplayName("Each status offers only the steps that make sense")
    void steps() {
        assertThat(ComplaintWorkflow.allowedSteps(Status.RECEIVED, null)).containsExactlyInAnyOrder(Step.START, Step.WITHDRAW);
        assertThat(ComplaintWorkflow.allowedSteps(Status.UNDER_INVESTIGATION, null)).containsExactlyInAnyOrder(Step.FINDING, Step.WITHDRAW);
        assertThat(ComplaintWorkflow.allowedSteps(Status.FINDING_MADE, Finding.SUBSTANTIATED)).containsExactly(Step.ACTION);
        assertThat(ComplaintWorkflow.allowedSteps(Status.FINDING_MADE, Finding.INCONCLUSIVE)).containsExactly(Step.ACTION);
        assertThat(ComplaintWorkflow.allowedSteps(Status.ACTION_TAKEN, Finding.SUBSTANTIATED)).containsExactly(Step.CLOSE);
    }

    @Test @DisplayName("An unsubstantiated complaint may be closed straight after the finding")
    void unsubstantiatedShortcut() {
        assertThat(ComplaintWorkflow.allowedSteps(Status.FINDING_MADE, Finding.UNSUBSTANTIATED)).containsExactlyInAnyOrder(Step.ACTION, Step.CLOSE);
    }

    @Test @DisplayName("Closed and withdrawn complaints are final")
    void finals() {
        assertThat(ComplaintWorkflow.allowedSteps(Status.CLOSED, Finding.SUBSTANTIATED)).isEmpty();
        assertThat(ComplaintWorkflow.allowedSteps(Status.WITHDRAWN, null)).isEmpty();
        assertThat(ComplaintWorkflow.isOpen(Status.CLOSED)).isFalse();
        assertThat(ComplaintWorkflow.isOpen(Status.WITHDRAWN)).isFalse();
        assertThat(ComplaintWorkflow.isOpen(Status.FINDING_MADE)).isTrue();
    }

    @Test @DisplayName("Details are editable only before a finding")
    void editable() {
        assertThat(ComplaintWorkflow.isEditable(Status.RECEIVED)).isTrue();
        assertThat(ComplaintWorkflow.isEditable(Status.UNDER_INVESTIGATION)).isTrue();
        assertThat(ComplaintWorkflow.isEditable(Status.FINDING_MADE)).isFalse();
        assertThat(ComplaintWorkflow.isEditable(Status.CLOSED)).isFalse();
    }

    @Test @DisplayName("Critical severity and the serious categories are urgent")
    void urgent() {
        assertThat(ComplaintWorkflow.isUrgent(Category.LATENESS, Severity.CRITICAL)).isTrue();
        for (Category c : Set.of(Category.THEFT, Category.EXCESSIVE_FORCE, Category.FIREARM_VIOLATION, Category.HARASSMENT))
            assertThat(ComplaintWorkflow.isUrgent(c, Severity.LOW)).isTrue();
        assertThat(ComplaintWorkflow.isUrgent(Category.LATENESS, Severity.HIGH)).isFalse();
    }

    @Test @DisplayName("Action must fit the finding")
    void actions() {
        assertThat(ComplaintWorkflow.validateAction(Finding.UNSUBSTANTIATED, Action.WRITTEN_WARNING, "x")).contains("not substantiated");
        assertThat(ComplaintWorkflow.validateAction(Finding.UNSUBSTANTIATED, Action.NO_ACTION, null)).isNull();
        assertThat(ComplaintWorkflow.validateAction(Finding.SUBSTANTIATED, Action.NO_ACTION, " ")).contains("why no action");
        assertThat(ComplaintWorkflow.validateAction(Finding.SUBSTANTIATED, Action.NO_ACTION, "Already counselled")).isNull();
        assertThat(ComplaintWorkflow.validateAction(Finding.SUBSTANTIATED, Action.VERBAL_WARNING, null)).isNull();
        assertThat(ComplaintWorkflow.validateAction(Finding.INCONCLUSIVE, Action.RETRAINING, null)).isNull();
    }
}
