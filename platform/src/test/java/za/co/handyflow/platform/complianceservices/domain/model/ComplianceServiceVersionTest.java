package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceServiceVersionTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2027, 1, 1);

    private static ComplianceServiceVersion.Content content(String name, String change) {
        return new ComplianceServiceVersion.Content(name, "cipc", "desc", "note", "Excludes CIPC fees", new BigDecimal("3.5"), 14, "consultant", true,
                "fixed", "on_delivery", change);
    }

    private static ComplianceServiceVersion draft(int number) {
        ComplianceServiceVersion v = ComplianceServiceVersion.draft(TenantId.generate(), UUID.randomUUID(), number, AUTHOR);
        v.edit(content("CIPC annual return", number > 1 ? "Hours revised" : null));
        return v;
    }

    @Test @DisplayName("a draft keeps the cleaned-up content")
    void edit() {
        var v = draft(1);
        assertThat(v.isDraft()).isTrue();
        assertThat(v.getCategory()).isEqualTo("CIPC");
        assertThat(v.getPricingModel()).isEqualTo("FIXED");
        assertThat(v.getBillingTrigger()).isEqualTo("ON_DELIVERY");
        assertThat(v.getResponsibleRole()).isEqualTo("CONSULTANT");
        assertThat(v.getEstimatedHours()).isEqualByComparingTo("3.50");
        assertThat(v.isRequiresReview()).isTrue();
    }

    @Test @DisplayName("a bad choice or number is refused and nothing changes")
    void editRefused() {
        var v = draft(1);
        assertThatThrownBy(() -> v.edit(new ComplianceServiceVersion.Content("x", "tax", null, null, null, null, null, "client", true, "fixed", "on_delivery", null)))
                .hasMessageContaining("category");
        assertThatThrownBy(() -> v.edit(new ComplianceServiceVersion.Content("x", "cipc", null, null, null, BigDecimal.ZERO, null, "client", true, "fixed", "on_delivery", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(v.getName()).isEqualTo("CIPC annual return");
    }

    @Test @DisplayName("sending for review needs a deliverable and, after version 1, what changed")
    void submit() {
        var v = draft(1);
        assertThatThrownBy(() -> v.submit(AUTHOR, 0)).hasMessageContaining("deliverable");
        v.submit(AUTHOR, 2);
        assertThat(v.getReviewStatus()).isEqualTo("IN_REVIEW");
        var second = ComplianceServiceVersion.draft(TenantId.generate(), UUID.randomUUID(), 2, AUTHOR);
        second.edit(content("CIPC annual return", null));
        assertThatThrownBy(() -> second.submit(AUTHOR, 1)).hasMessageContaining("what changed");
    }

    @Test @DisplayName("a second person approves and the version is then dated and locked")
    void approve() {
        var v = draft(1);
        v.submit(AUTHOR, 1);
        assertThatThrownBy(() -> v.approve(REVIEWER, null, null, null)).hasMessageContaining("date");
        v.approve(REVIEWER, FROM, null, "Looks right");
        assertThat(v.isApproved()).isTrue();
        assertThat(v.getEffectiveFrom()).isEqualTo(FROM);
        assertThat(v.getReviewExceptionReason()).isNull();
        assertThatThrownBy(() -> v.edit(content("Changed", null))).hasMessageContaining("new version");
    }

    @Test @DisplayName("the author approving needs a reason that is kept")
    void sameApprover() {
        var v = draft(1);
        v.submit(AUTHOR, 1);
        assertThatThrownBy(() -> v.approve(AUTHOR, FROM, null, null)).hasMessageContaining("someone else");
        v.approve(AUTHOR, FROM, "Sole practitioner, nobody else to check", null);
        assertThat(v.getReviewExceptionReason()).isEqualTo("Sole practitioner, nobody else to check");
    }

    @Test @DisplayName("sending back needs a reason and returns it to draft")
    void reject() {
        var v = draft(1);
        v.submit(AUTHOR, 1);
        assertThatThrownBy(() -> v.reject(REVIEWER, "no")).isInstanceOf(IllegalArgumentException.class);
        v.reject(REVIEWER, "Hours look too low for this");
        assertThat(v.isDraft()).isTrue();
        assertThat(v.getReviewNote()).isEqualTo("Hours look too low for this");
    }

    @Test @DisplayName("an approved version ends on a date and is then not in force after it")
    void ends() {
        var v = draft(1);
        v.submit(AUTHOR, 1);
        v.approve(REVIEWER, FROM, null, null);
        assertThatThrownBy(() -> v.endOn(FROM.minusDays(1))).hasMessageContaining("cannot end before");
        v.endOn(FROM.plusMonths(6));
        assertThat(ObligationRules.effectiveOn(java.util.List.of(v.span()), FROM.plusMonths(1))).isEqualTo(1);
        assertThat(ObligationRules.effectiveOn(java.util.List.of(v.span()), FROM.plusMonths(7))).isNull();
    }
}
