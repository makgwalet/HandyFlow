package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceRegChangeTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final UUID RAISER = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();
    private static final String NOTE = "Checked against the gazette notice";

    private ComplianceRegChange change(String type, LocalDate effective) {
        return ComplianceRegChange.raise(TENANT, UUID.randomUUID(), "  Annual return fees  ", " new fees ", type, TODAY.minusDays(3), effective, null, RAISER, TODAY);
    }

    @Test @DisplayName("a new change is detected, cleaned, and not yet reviewed")
    void raised() {
        ComplianceRegChange c = change("AMENDED", TODAY.plusDays(30));
        assertThat(c.getStatus()).isEqualTo("DETECTED");
        assertThat(c.getTitle()).isEqualTo("Annual return fees");
        assertThat(c.getOrigin()).isEqualTo("MANUAL");
        assertThat(c.getReviewedBy()).isNull();
    }

    @Test @DisplayName("it must go into review before it can be confirmed")
    void reviewFirst() {
        ComplianceRegChange c = change("AMENDED", TODAY.plusDays(30));
        assertThatThrownBy(() -> c.confirm(REVIEWER, NOTE, null)).isInstanceOf(IllegalStateException.class);
        c.startReview(REVIEWER);
        assertThat(c.getStatus()).isEqualTo("IN_REVIEW");
        assertThatThrownBy(() -> c.startReview(REVIEWER)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a second person confirms with a note, and the decision is recorded")
    void confirmed() {
        ComplianceRegChange c = change("AMENDED", TODAY.plusDays(30));
        c.startReview(REVIEWER);
        c.confirm(REVIEWER, NOTE, null);
        assertThat(c.getStatus()).isEqualTo("CONFIRMED");
        assertThat(c.getReviewedBy()).isEqualTo(REVIEWER);
        assertThat(c.getReviewNote()).isEqualTo(NOTE);
        assertThat(c.getReviewExceptionReason()).isNull();
    }

    @Test @DisplayName("the person who raised it needs a reason, and it is kept")
    void sameperson() {
        ComplianceRegChange c = change("AMENDED", TODAY.plusDays(30));
        c.startReview(RAISER);
        assertThatThrownBy(() -> c.confirm(RAISER, NOTE, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(c.getStatus()).isEqualTo("IN_REVIEW");
        c.confirm(RAISER, NOTE, "No one else is available this week");
        assertThat(c.getReviewExceptionReason()).isEqualTo("No one else is available this week");
    }

    @Test @DisplayName("an amendment with no effective date cannot be confirmed, but a proposal can")
    void effectiveDate() {
        ComplianceRegChange amended = change("AMENDED", null);
        amended.startReview(REVIEWER);
        assertThatThrownBy(() -> amended.confirm(REVIEWER, NOTE, null)).isInstanceOf(IllegalStateException.class);
        ComplianceRegChange proposed = change("PROPOSED", null);
        proposed.startReview(REVIEWER);
        proposed.confirm(REVIEWER, NOTE, null);
        assertThat(proposed.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test @DisplayName("a decided change cannot be edited or decided again, and dismissing needs a note too")
    void decided() {
        ComplianceRegChange c = change("NEW", TODAY.plusDays(10));
        assertThatThrownBy(() -> c.dismiss(REVIEWER, "no", null)).isInstanceOf(IllegalArgumentException.class);
        c.dismiss(REVIEWER, "Does not apply to our clients at all", null);
        assertThat(c.getStatus()).isEqualTo("DISMISSED");
        assertThatThrownBy(() -> c.dismiss(REVIEWER, NOTE, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.edit(UUID.randomUUID(), "x", null, "NEW", null, null, null, TODAY)).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a title is required and a bad date or web address is refused")
    void validation() {
        assertThatThrownBy(() -> ComplianceRegChange.raise(TENANT, UUID.randomUUID(), " ", null, "NEW", null, null, null, RAISER, TODAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplianceRegChange.raise(TENANT, UUID.randomUUID(), "T", null, "NEW", TODAY.plusDays(1), null, null, RAISER, TODAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ComplianceRegChange.raise(TENANT, UUID.randomUUID(), "T", null, "NEW", null, null, "file:///etc/passwd", RAISER, TODAY)).isInstanceOf(IllegalArgumentException.class);
    }
}
