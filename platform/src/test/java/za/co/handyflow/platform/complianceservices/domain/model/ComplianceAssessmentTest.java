package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceAssessmentTest {

    private static final UUID ME = UUID.randomUUID();

    private static ComplianceAssessmentItem item(String applicability, String evidence) {
        return ComplianceAssessmentItem.of(null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "POPIA-1", "Information officer", "HIGH", applicability, evidence);
    }

    @Test @DisplayName("a new item starts as not assessed, whatever the evidence says")
    void startsUnassessed() {
        assertThat(item("APPLICABLE", "MET").getStatus()).isEqualTo("NOT_ASSESSED");
        assertThat(item("NOT_APPLICABLE", "MISSING").getStatus()).isEqualTo("NOT_APPLICABLE");
        assertThat(item("APPLICABLE", "MISSING").getStatus()).isEqualTo("EVIDENCE_MISSING");
    }

    @Test @DisplayName("judging recomputes the status and records who")
    void judges() {
        var i = item("APPLICABLE", "MET");
        i.judge("yes", null, null, ME);
        assertThat(i.getStatus()).isEqualTo("COMPLIANT");
        assertThat(i.getAssessedBy()).isEqualTo(ME);
        assertThat(i.getAssessedAt()).isNotNull();
    }

    @Test @DisplayName("no or partial needs a note saying what is missing")
    void needsNote() {
        var i = item("APPLICABLE", "MET");
        assertThatThrownBy(() -> i.judge("NO", null, "short", ME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> i.judge("PARTIAL", null, null, ME)).isInstanceOf(IllegalArgumentException.class);
        i.judge("NO", "REMEDIATION", "No information officer has been appointed yet", ME);
        assertThat(i.getStatus()).isEqualTo("NON_COMPLIANT");
        assertThat(i.displayStatus()).isEqualTo("REMEDIATION_IN_PROGRESS");
    }

    @Test @DisplayName("a rejected judgement leaves the item as it was")
    void rejectedChangesNothing() {
        var i = item("APPLICABLE", "MET");
        i.judge("YES", null, null, ME);
        assertThatThrownBy(() -> i.judge("NO", null, "x", ME)).isInstanceOf(IllegalArgumentException.class);
        assertThat(i.getStatus()).isEqualTo("COMPLIANT");
    }

    @Test @DisplayName("a person is required to judge")
    void personRequired() {
        assertThatThrownBy(() -> item("APPLICABLE", "MET").judge("YES", null, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("checking evidence again keeps the person's judgement but can change the status")
    void recheckKeepsJudgement() {
        var i = item("APPLICABLE", "MET");
        i.judge("YES", null, null, ME);
        i.recheck("EXPIRED");
        assertThat(i.getControl()).isEqualTo("YES");
        assertThat(i.getStatus()).isEqualTo("EXPIRED");
        i.recheck("MET");
        assertThat(i.getStatus()).isEqualTo("COMPLIANT");
    }

    @Test @DisplayName("completing freezes the assessment")
    void completes() {
        var a = ComplianceAssessment.start(null, UUID.randomUUID(), LocalDate.of(2026, 11, 24), AssessmentRules.Weights.defaults(), ME);
        assertThat(a.isDraft()).isTrue();
        a.complete(new AssessmentRules.Score(new BigDecimal("80.0"), 4, 1), "  Reviewed with the client  ", ME);
        assertThat(a.getStatus()).isEqualTo("COMPLETED");
        assertThat(a.getScorePercent()).isEqualByComparingTo(new BigDecimal("80.0"));
        assertThat(a.getSummaryNote()).isEqualTo("Reviewed with the client");
        assertThat(a.getCompletedAt()).isNotNull();
        assertThatThrownBy(() -> a.complete(new AssessmentRules.Score(null, 0, 0), null, ME)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(a::requireDraft).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("the weights an assessment started with are kept on it")
    void keepsWeights() {
        var w = new AssessmentRules.Weights(1, 3, 5, 10);
        var a = ComplianceAssessment.start(null, UUID.randomUUID(), LocalDate.of(2026, 11, 24), w, ME);
        assertThat(a.weights()).isEqualTo(w);
    }
}
