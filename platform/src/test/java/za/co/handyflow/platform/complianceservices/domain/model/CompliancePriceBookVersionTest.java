package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompliancePriceBookVersionTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2027, 1, 1);

    private static CompliancePriceBookVersion draft(int number) {
        CompliancePriceBookVersion v = CompliancePriceBookVersion.draft(TenantId.generate(), number, AUTHOR);
        v.edit("  2027 prices ", number > 1 ? "Retainers up 6%" : null);
        return v;
    }

    private static CompliancePriceBookVersion inReview(int number) {
        var v = draft(number);
        v.submit(AUTHOR, 3, 0);
        return v;
    }

    @Test @DisplayName("a draft keeps the cleaned-up label")
    void edit() {
        var v = draft(1);
        assertThat(v.isDraft()).isTrue();
        assertThat(v.getLabel()).isEqualTo("2027 prices");
        assertThat(v.getCurrency()).isEqualTo("ZAR");
    }

    @Test @DisplayName("review needs at least one line, and every line complete")
    void submitNeeds() {
        assertThatThrownBy(() -> draft(1).submit(AUTHOR, 0, 0)).hasMessageContaining("at least one");
        assertThatThrownBy(() -> draft(1).submit(AUTHOR, 4, 2)).hasMessageContaining("2 line(s)");
        var v = draft(1);
        v.submit(AUTHOR, 4, 0);
        assertThat(v.getReviewStatus()).isEqualTo("IN_REVIEW");
    }

    @Test @DisplayName("a later version must say what changed")
    void changeSummary() {
        var v = CompliancePriceBookVersion.draft(TenantId.generate(), 2, AUTHOR);
        v.edit("2028", null);
        assertThatThrownBy(() -> v.submit(AUTHOR, 3, 0)).hasMessageContaining("what changed");
    }

    @Test @DisplayName("someone else approves it and it takes effect on a date")
    void approve() {
        var v = inReview(1);
        v.approve(REVIEWER, FROM, null, "ok");
        assertThat(v.isApproved()).isTrue();
        assertThat(v.getEffectiveFrom()).isEqualTo(FROM);
        assertThat(v.getReviewExceptionReason()).isNull();
        assertThat(v.span().approved()).isTrue();
    }

    @Test @DisplayName("the author approves only with a recorded reason")
    void selfApproval() {
        var v = inReview(1);
        assertThatThrownBy(() -> v.approve(AUTHOR, FROM, null, null)).hasMessageContaining("someone else");
        assertThatThrownBy(() -> v.approve(AUTHOR, FROM, "too short", null)).hasMessageContaining("at least 15");
        v.approve(AUTHOR, FROM, "Only partner in the practice", null);
        assertThat(v.getReviewExceptionReason()).isEqualTo("Only partner in the practice");
    }

    @Test @DisplayName("approval needs a start date and a version in review")
    void approveNeeds() {
        assertThatThrownBy(() -> inReview(1).approve(REVIEWER, null, null, null)).hasMessageContaining("date");
        assertThatThrownBy(() -> draft(1).approve(REVIEWER, FROM, null, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("rejecting returns it to draft with the reason")
    void reject() {
        var v = inReview(1);
        assertThatThrownBy(() -> v.reject(REVIEWER, "no")).hasMessageContaining("at least 10");
        v.reject(REVIEWER, "Retainer rates need checking");
        assertThat(v.isDraft()).isTrue();
        assertThat(v.getReviewNote()).isEqualTo("Retainer rates need checking");
    }

    @Test @DisplayName("an approved version is never edited, and can only end on or after its start")
    void locked() {
        var v = inReview(1);
        v.approve(REVIEWER, FROM, null, null);
        assertThatThrownBy(() -> v.edit("x", "y")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> v.endOn(FROM.minusDays(1))).hasMessageContaining("before it starts");
        v.endOn(FROM.plusMonths(6));
        assertThat(v.getEffectiveTo()).isEqualTo(FROM.plusMonths(6));
        assertThatThrownBy(() -> draft(1).endOn(FROM)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a line is cleaned and checked on its model")
    void line() {
        var tenant = TenantId.generate();
        var l = CompliancePriceBookLine.of(tenant, UUID.randomUUID(), new CompliancePriceBookLine.Input("cipc annual return", "fixed", new BigDecimal("1500"),
                null, null, new BigDecimal("1000"), new BigDecimal("25"), null, " note "), 0);
        assertThat(l.getServiceCode()).isEqualTo("CIPC-ANNUAL-RETURN");
        assertThat(l.getPricingModel()).isEqualTo("FIXED");
        assertThat(l.getTaxTreatment()).isEqualTo("STANDARD");
        assertThat(l.getNotes()).isEqualTo("note");
        assertThat(l.gaps()).isEmpty();
        var hourly = CompliancePriceBookLine.of(tenant, UUID.randomUUID(), new CompliancePriceBookLine.Input("OHS_AUDIT", "hourly", null, null, null, null, null,
                "zero_rated", null), 1);
        assertThat(hourly.gaps()).containsExactly("an hourly grade");
        assertThat(hourly.getTaxTreatment()).isEqualTo("ZERO_RATED");
    }

    @Test @DisplayName("a line with bad amounts or a bad model is refused")
    void lineRefused() {
        var t = TenantId.generate();
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> CompliancePriceBookLine.of(t, id, new CompliancePriceBookLine.Input("X1", "fixed", new BigDecimal("-1"), null, null, null, null, null, null), 0))
                .hasMessageContaining("Base fee");
        assertThatThrownBy(() -> CompliancePriceBookLine.of(t, id, new CompliancePriceBookLine.Input("X1", "fixed", new BigDecimal("1"), null, null, null, new BigDecimal("300"), null, null), 0))
                .hasMessageContaining("Urgent surcharge");
        assertThatThrownBy(() -> CompliancePriceBookLine.of(t, id, new CompliancePriceBookLine.Input("X1", "free", null, null, null, null, null, null, null), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompliancePriceBookLine.of(t, id, new CompliancePriceBookLine.Input("X1", null, null, null, null, null, null, null, null), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("a rate needs an hourly figure and a clean grade")
    void rate() {
        var t = TenantId.generate();
        var id = UUID.randomUUID();
        var r = CompliancePriceBookRate.of(t, id, "senior consultant", new BigDecimal("950"), new BigDecimal("420"), 0);
        assertThat(r.getGrade()).isEqualTo("SENIOR_CONSULTANT");
        assertThat(r.getCostRate()).isEqualByComparingTo("420");
        assertThatThrownBy(() -> CompliancePriceBookRate.of(t, id, "SENIOR", null, null, 0)).hasMessageContaining("hourly rate");
        assertThatThrownBy(() -> CompliancePriceBookRate.of(t, id, "x", new BigDecimal("1"), null, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
