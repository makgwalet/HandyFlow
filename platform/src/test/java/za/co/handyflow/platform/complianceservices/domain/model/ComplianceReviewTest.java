package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceReviewTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID PREPARER = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();
    private static final String NOTE = "Checked every figure against the evidence";

    private ComplianceReviewTemplate template() { return ComplianceReviewTemplate.create(TENANT, "Assessment check", "ASSESSMENT", null, PREPARER); }

    private ComplianceReview review() {
        return ComplianceReview.start(TENANT, CLIENT, "ASSESSMENT", UUID.randomUUID(), "Assessment as of 2026-11-01", template(), PREPARER, false, REVIEWER);
    }

    private static List<QualityRules.Line> pass() { return List.of(new QualityRules.Line(1, true, "PASS"), new QualityRules.Line(2, false, "PASS")); }

    @Test @DisplayName("a review starts in progress with the template's name copied on, round one")
    void started() {
        ComplianceReview r = review();
        assertThat(r.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(r.getRound()).isEqualTo(1);
        assertThat(r.getTemplateName()).isEqualTo("Assessment check");
        assertThat(r.getReviewedBy()).isNull();
    }

    @Test @DisplayName("a checklist for another kind of work, or one that is switched off, cannot be used")
    void templateMismatch() {
        assertThatThrownBy(() -> ComplianceReview.start(TENANT, CLIENT, "CASE", UUID.randomUUID(), "A case", template(), null, false, REVIEWER)).isInstanceOf(IllegalArgumentException.class);
        ComplianceReviewTemplate off = template();
        off.setActive(false);
        assertThatThrownBy(() -> ComplianceReview.start(TENANT, CLIENT, "ASSESSMENT", UUID.randomUUID(), "x", off, null, false, REVIEWER)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a second person approves with a summary, and it cannot be completed twice")
    void approved() {
        ComplianceReview r = review();
        r.complete("approved", REVIEWER, NOTE, null, pass());
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(r.getReviewedBy()).isEqualTo(REVIEWER);
        assertThat(r.getCompletedAt() != null).isTrue();
        assertThatThrownBy(() -> r.complete("APPROVED", REVIEWER, NOTE, null, pass())).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("the person who prepared the work needs a reason, and nothing changes if it is missing")
    void samePerson() {
        ComplianceReview r = review();
        assertThatThrownBy(() -> r.complete("APPROVED", PREPARER, NOTE, null, pass())).isInstanceOf(IllegalArgumentException.class);
        assertThat(r.getStatus()).isEqualTo("IN_PROGRESS");
        r.complete("APPROVED", PREPARER, NOTE, "No one else qualified is in today", pass());
        assertThat(r.getExceptionReason()).isEqualTo("No one else qualified is in today");
    }

    @Test @DisplayName("a critical failure cannot be approved, and a short summary is refused")
    void refused() {
        ComplianceReview r = review();
        List<QualityRules.Line> failed = List.of(new QualityRules.Line(1, true, "FAIL"), new QualityRules.Line(2, false, "PASS"));
        assertThatThrownBy(() -> r.complete("APPROVED", REVIEWER, NOTE, null, failed)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> r.complete("REWORK_REQUIRED", REVIEWER, "bad", null, failed)).isInstanceOf(IllegalArgumentException.class);
        assertThat(r.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test @DisplayName("work sent back gets a next round that starts again, linked to the one before")
    void nextRound() {
        ComplianceReview r = review();
        assertThatThrownBy(() -> r.nextRound(REVIEWER)).isInstanceOf(IllegalStateException.class);
        r.complete("REWORK_REQUIRED", REVIEWER, NOTE, null, List.of(new QualityRules.Line(1, false, "FAIL")));
        ComplianceReview next = r.nextRound(REVIEWER);
        assertThat(next.getRound()).isEqualTo(2);
        assertThat(next.getPreviousReviewId()).isEqualTo(r.getId());
        assertThat(next.getSubjectId()).isEqualTo(r.getSubjectId());
        assertThat(next.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(next.getPreparedBy()).isEqualTo(PREPARER);
    }

    @Test @DisplayName("marking a check keeps its note, and a failed one needs one")
    void marking() {
        ComplianceReviewItem item = ComplianceReviewItem.copyOf(TENANT, UUID.randomUUID(), 1, "VAT number is right", true);
        assertThat(item.getResult()).isEqualTo("PENDING");
        assertThatThrownBy(() -> item.mark("FAIL", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(item.getResult()).isEqualTo("PENDING");
        item.mark("FAIL", "The VAT number is missing a digit");
        assertThat(item.getResult()).isEqualTo("FAIL");
        assertThat(item.line().critical()).isTrue();
    }

    @Test @DisplayName("a checklist needs at least one check and not too many")
    void checklist() {
        assertThatThrownBy(() -> ComplianceReviewTemplate.checkItems(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplianceReviewTemplate.checkItems(java.util.Collections.nCopies(41, "x"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplianceReviewTemplate.checkItems(List.of(" "))).isInstanceOf(IllegalArgumentException.class);
        ComplianceReviewTemplate.checkItems(List.of("One check"));
    }
}
