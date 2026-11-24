package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceObligationVersionTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();

    private static ComplianceObligationVersion.Content content(String name, String source, String change) {
        return new ComplianceObligationVersion.Content(name, "desc", source, "CIPC", null, "annual", "annual_fixed", 6, 30, null, null,
                null, "high", "consultant", "guidance", change);
    }

    private static ComplianceObligationVersion draft(int number) {
        ComplianceObligationVersion v = ComplianceObligationVersion.draft(TenantId.generate(), UUID.randomUUID(), number, AUTHOR);
        v.edit(content("Annual return", "Companies Act s33", number > 1 ? "Due date moved" : null), AUTHOR);
        return v;
    }

    @Test @DisplayName("a draft keeps the cleaned-up content")
    void edit() {
        ComplianceObligationVersion v = draft(1);
        assertThat(v.isDraft()).isTrue();
        assertThat(v.getFrequency()).isEqualTo("ANNUAL");
        assertThat(v.getDueRuleType()).isEqualTo("ANNUAL_FIXED");
        assertThat(v.getRiskLevel()).isEqualTo("HIGH");
        assertThat(v.getJurisdiction()).isEqualTo("ZA");
    }

    @Test @DisplayName("a bad due rule or choice is refused")
    void editRefused() {
        ComplianceObligationVersion v = draft(1);
        assertThatThrownBy(() -> v.edit(new ComplianceObligationVersion.Content("x", null, "s", null, null, "annual", "annual_fixed", 13, 1,
                null, null, null, "high", "client", null, null), AUTHOR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> v.edit(new ComplianceObligationVersion.Content("x", null, "s", null, null, "sometimes", "none", null, null,
                null, null, null, "high", "client", null, null), AUTHOR)).hasMessageContaining("frequency");
        assertThat(v.getName()).isEqualTo("Annual return");
    }

    @Test @DisplayName("review needs the essentials, and a later version needs to say what changed")
    void submitNeedsEssentials() {
        ComplianceObligationVersion empty = ComplianceObligationVersion.draft(TenantId.generate(), UUID.randomUUID(), 1, AUTHOR);
        assertThatThrownBy(() -> empty.submit(AUTHOR)).hasMessageContaining("legal source");
        ComplianceObligationVersion second = ComplianceObligationVersion.draft(TenantId.generate(), UUID.randomUUID(), 2, AUTHOR);
        second.edit(content("Annual return", "Companies Act s33", null), AUTHOR);
        assertThatThrownBy(() -> second.submit(AUTHOR)).hasMessageContaining("what changed");
    }

    @Test @DisplayName("someone else approves it and it takes effect on the date given")
    void approve() {
        ComplianceObligationVersion v = draft(1);
        v.submit(AUTHOR);
        assertThat(v.getReviewStatus()).isEqualTo("IN_REVIEW");
        v.approve(REVIEWER, LocalDate.of(2026, 12, 1), null, "checked against the Act");
        assertThat(v.isApproved()).isTrue();
        assertThat(v.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(v.getReviewExceptionReason()).isNull();
    }

    @Test @DisplayName("the person who wrote or sent it needs a recorded reason to approve it")
    void selfApprove() {
        ComplianceObligationVersion v = draft(1);
        v.submit(AUTHOR);
        assertThatThrownBy(() -> v.approve(AUTHOR, LocalDate.of(2026, 12, 1), null, null)).hasMessageContaining("someone else");
        assertThatThrownBy(() -> v.approve(AUTHOR, LocalDate.of(2026, 12, 1), "short", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(v.isApproved()).isFalse();
        v.approve(AUTHOR, LocalDate.of(2026, 12, 1), "Sole practitioner, no second reviewer", null);
        assertThat(v.getReviewExceptionReason()).isEqualTo("Sole practitioner, no second reviewer");
    }

    @Test @DisplayName("approval needs a start date and a version in review")
    void approveRules() {
        ComplianceObligationVersion v = draft(1);
        assertThatThrownBy(() -> v.approve(REVIEWER, LocalDate.of(2026, 12, 1), null, null)).isInstanceOf(IllegalStateException.class);
        v.submit(AUTHOR);
        assertThatThrownBy(() -> v.approve(REVIEWER, null, null, null)).hasMessageContaining("date");
    }

    @Test @DisplayName("sending it back needs a reason, and returns it to draft")
    void reject() {
        ComplianceObligationVersion v = draft(1);
        v.submit(AUTHOR);
        assertThatThrownBy(() -> v.reject(REVIEWER, "no")).isInstanceOf(IllegalArgumentException.class);
        v.reject(REVIEWER, "Cite the section, not the Act");
        assertThat(v.isDraft()).isTrue();
        assertThat(v.getReviewNote()).isEqualTo("Cite the section, not the Act");
    }

    @Test @DisplayName("an approved version cannot be edited, sent again, or approved again")
    void locked() {
        ComplianceObligationVersion v = draft(1);
        v.submit(AUTHOR);
        v.approve(REVIEWER, LocalDate.of(2026, 12, 1), null, null);
        assertThatThrownBy(() -> v.edit(content("Changed", "x", null), AUTHOR)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> v.submit(AUTHOR)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> v.approve(REVIEWER, LocalDate.of(2027, 1, 1), null, null)).isInstanceOf(IllegalStateException.class);
        assertThat(v.getName()).isEqualTo("Annual return");
    }

    @Test @DisplayName("an approved version can be ended, but not before it starts")
    void endOn() {
        ComplianceObligationVersion v = draft(1);
        assertThatThrownBy(() -> v.endOn(LocalDate.of(2026, 12, 31))).isInstanceOf(IllegalStateException.class);
        v.submit(AUTHOR);
        v.approve(REVIEWER, LocalDate.of(2026, 12, 1), null, null);
        assertThatThrownBy(() -> v.endOn(LocalDate.of(2026, 11, 30))).isInstanceOf(IllegalArgumentException.class);
        v.endOn(LocalDate.of(2027, 3, 31));
        assertThat(v.getEffectiveTo()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(v.span().to()).isEqualTo(LocalDate.of(2027, 3, 31));
    }

    @Test @DisplayName("a new draft copies the earlier version's text, not its change note")
    void copy() {
        ComplianceObligationVersion first = draft(1);
        ComplianceObligationVersion next = ComplianceObligationVersion.draft(TenantId.generate(), UUID.randomUUID(), 2, AUTHOR);
        next.copyFrom(first, AUTHOR);
        assertThat(next.getName()).isEqualTo("Annual return");
        assertThat(next.getLegalSource()).isEqualTo("Companies Act s33");
        assertThat(next.getChangeSummary()).isNull();
        assertThat(next.isDraft()).isTrue();
    }
}
