package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentRulesTest {

    private static AssessmentRules.EvidenceCheck ev(boolean mandatory, String result) { return new AssessmentRules.EvidenceCheck("Doc", mandatory, result); }

    @Test @DisplayName("evidence: optional documents never count against an obligation")
    void optionalIgnored() {
        assertThat(AssessmentRules.evidenceStatus(List.of(ev(false, "MISSING")))).isEqualTo("NOT_REQUIRED");
        assertThat(AssessmentRules.evidenceStatus(List.of())).isEqualTo("NOT_REQUIRED");
    }

    @Test @DisplayName("evidence: the worst mandatory result wins")
    void worstWins() {
        assertThat(AssessmentRules.evidenceStatus(List.of(ev(true, "MET"), ev(true, "PENDING")))).isEqualTo("PENDING");
        assertThat(AssessmentRules.evidenceStatus(List.of(ev(true, "PENDING"), ev(true, "EXPIRED")))).isEqualTo("EXPIRED");
        assertThat(AssessmentRules.evidenceStatus(List.of(ev(true, "EXPIRED"), ev(true, "MISSING")))).isEqualTo("MISSING");
        assertThat(AssessmentRules.evidenceStatus(List.of(ev(true, "MET"), ev(false, "MISSING")))).isEqualTo("MET");
    }

    @Test @DisplayName("a document on file is not enough: with no judgement the obligation is not assessed")
    void documentIsNotCompliance() {
        assertThat(AssessmentRules.status("APPLICABLE", "MET", "UNKNOWN")).isEqualTo("NOT_ASSESSED");
        assertThat(AssessmentRules.status("APPLICABLE", "NOT_REQUIRED", "UNKNOWN")).isEqualTo("NOT_ASSESSED");
    }

    @Test @DisplayName("status follows applicability, evidence and the person's control judgement")
    void statuses() {
        assertThat(AssessmentRules.status("APPLICABLE", "MET", "YES")).isEqualTo("COMPLIANT");
        assertThat(AssessmentRules.status("APPLICABLE", "NOT_REQUIRED", "YES")).isEqualTo("COMPLIANT");
        assertThat(AssessmentRules.status("APPLICABLE", "MET", "PARTIAL")).isEqualTo("PARTIALLY_COMPLIANT");
        assertThat(AssessmentRules.status("APPLICABLE", "MET", "NO")).isEqualTo("NON_COMPLIANT");
        assertThat(AssessmentRules.status("APPLICABLE", "MISSING", "YES")).isEqualTo("EVIDENCE_MISSING");
        assertThat(AssessmentRules.status("APPLICABLE", "EXPIRED", "YES")).isEqualTo("EXPIRED");
        assertThat(AssessmentRules.status("APPLICABLE", "PENDING", "YES")).isEqualTo("UNDER_REVIEW");
        assertThat(AssessmentRules.status("NOT_APPLICABLE", "MISSING", "NO")).isEqualTo("NOT_APPLICABLE");
        assertThat(AssessmentRules.status("PENDING", "MET", "YES")).isEqualTo("NOT_ASSESSED");
    }

    @Test @DisplayName("a control that is not in place is non-compliant whatever the evidence says")
    void controlNoWins() {
        assertThat(AssessmentRules.status("APPLICABLE", "MISSING", "NO")).isEqualTo("NON_COMPLIANT");
    }

    @Test @DisplayName("a problem being worked on shows that, but is still scored as the problem")
    void workflowDisplay() {
        assertThat(AssessmentRules.display("NON_COMPLIANT", "REMEDIATION")).isEqualTo("REMEDIATION_IN_PROGRESS");
        assertThat(AssessmentRules.display("EVIDENCE_MISSING", "AUTHORITY")).isEqualTo("AWAITING_AUTHORITY");
        assertThat(AssessmentRules.display("COMPLIANT", "REMEDIATION")).isEqualTo("COMPLIANT");
        assertThat(AssessmentRules.display("NON_COMPLIANT", "NONE")).isEqualTo("NON_COMPLIANT");
        assertThat(AssessmentRules.points("NON_COMPLIANT")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test @DisplayName("control and workflow are validated; a workflow needs a note")
    void validation() {
        assertThat(AssessmentRules.control(null)).isEqualTo("UNKNOWN");
        assertThat(AssessmentRules.control(" yes ")).isEqualTo("YES");
        assertThatThrownBy(() -> AssessmentRules.control("MAYBE")).isInstanceOf(IllegalArgumentException.class);
        assertThat(AssessmentRules.workflow(null, null)).isEqualTo("NONE");
        assertThatThrownBy(() -> AssessmentRules.workflow("REMEDIATION", "short")).isInstanceOf(IllegalArgumentException.class);
        assertThat(AssessmentRules.workflow("remediation", "Policy being drafted by the client")).isEqualTo("REMEDIATION");
        assertThatThrownBy(() -> AssessmentRules.workflow("SOMETHING", "a long enough note here")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("weights must be 1 to 100 and not fall as risk rises")
    void weights() {
        assertThat(AssessmentRules.Weights.defaults().of("CRITICAL")).isEqualTo(8);
        assertThat(AssessmentRules.Weights.defaults().of(null)).isEqualTo(2);
        assertThatThrownBy(() -> new AssessmentRules.Weights(0, 2, 4, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssessmentRules.Weights(1, 2, 4, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssessmentRules.Weights(1, 5, 4, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new AssessmentRules.Weights(3, 3, 3, 3).of("LOW")).isEqualTo(3);
    }

    private static AssessmentRules.Line line(String code, String risk, String status) { return new AssessmentRules.Line(code, risk, status); }

    @Test @DisplayName("score is weighted by risk, with partial worth half")
    void score() {
        // CRITICAL compliant 8, HIGH partial 2 of 4, LOW non-compliant 0 of 1 => 10 / 13
        var s = AssessmentRules.score(List.of(line("A", "CRITICAL", "COMPLIANT"), line("B", "HIGH", "PARTIALLY_COMPLIANT"), line("C", "LOW", "NON_COMPLIANT")),
                AssessmentRules.Weights.defaults());
        assertThat(s.percent()).isEqualByComparingTo(new BigDecimal("76.9"));
        assertThat(s.scored()).isEqualTo(3);
        assertThat(s.unscored()).isEqualTo(0);
    }

    @Test @DisplayName("not-applicable items are left out and not-assessed items are counted but never scored")
    void unscored() {
        var s = AssessmentRules.score(List.of(line("A", "HIGH", "COMPLIANT"), line("B", "HIGH", "NOT_APPLICABLE"), line("C", "HIGH", "NOT_ASSESSED")),
                AssessmentRules.Weights.defaults());
        assertThat(s.percent()).isEqualByComparingTo(new BigDecimal("100.0"));
        assertThat(s.scored()).isEqualTo(1);
        assertThat(s.unscored()).isEqualTo(1);
    }

    @Test @DisplayName("nothing scored means no percentage, not 0 or 100")
    void nothingScored() {
        assertThat(AssessmentRules.score(List.of(), AssessmentRules.Weights.defaults()).percent()).isNull();
        assertThat(AssessmentRules.score(List.of(line("A", "LOW", "NOT_ASSESSED")), AssessmentRules.Weights.defaults()).percent()).isNull();
    }

    @Test @DisplayName("an unknown risk level is weighed as medium")
    void unknownRisk() {
        var s = AssessmentRules.score(List.of(line("A", null, "COMPLIANT"), line("B", "LOW", "NON_COMPLIANT")), AssessmentRules.Weights.defaults());
        assertThat(s.percent()).isEqualByComparingTo(new BigDecimal("66.7"));
    }

    @Test @DisplayName("completion is blocked for applicable obligations with no judgement and no note")
    void blockers() {
        var items = List.of(
                new AssessmentRules.DraftItem("A", "APPLICABLE", "UNKNOWN", null),
                new AssessmentRules.DraftItem("B", "APPLICABLE", "UNKNOWN", "Waiting for the client to send the policy"),
                new AssessmentRules.DraftItem("C", "APPLICABLE", "YES", null),
                new AssessmentRules.DraftItem("D", "NOT_APPLICABLE", "UNKNOWN", null),
                new AssessmentRules.DraftItem("E", "PENDING", "UNKNOWN", null));
        assertThat(AssessmentRules.completionBlockers(items)).containsExactly("A");
    }

    @Test @DisplayName("changes show what moved, what is new and what is gone, with reasons")
    void changes() {
        var before = Map.of("A", new AssessmentRules.Snapshot("COMPLIANT", null), "B", new AssessmentRules.Snapshot("NON_COMPLIANT", null),
                "GONE", new AssessmentRules.Snapshot("COMPLIANT", null));
        var now = Map.of("A", new AssessmentRules.Snapshot("EXPIRED", "Certificate expired"), "B", new AssessmentRules.Snapshot("NON_COMPLIANT", "same"),
                "NEW", new AssessmentRules.Snapshot("COMPLIANT", null));
        var out = AssessmentRules.changes(before, now);
        assertThat(out).hasSize(3);
        assertThat(out.get(0).code()).isEqualTo("A");
        assertThat(out.get(0).from()).isEqualTo("COMPLIANT");
        assertThat(out.get(0).to()).isEqualTo("EXPIRED");
        assertThat(out.get(0).reason()).isEqualTo("Certificate expired");
        assertThat(out.get(1).code()).isEqualTo("GONE");
        assertThat(out.get(1).to()).isNull();
        assertThat(out.get(2).code()).isEqualTo("NEW");
        assertThat(out.get(2).from()).isNull();
    }
}
