package za.co.handyflow.platform.compliancetender.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenderTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    private Tender freshTender() {
        return Tender.create(TENANT, "TND-00001", "Municipal Road Upgrade", "City of Cape Town",
                "CCT-2026-045", null, null, null, new BigDecimal("12500000"), "Construction",
                "cidb Grade 6GB", USER);
    }

    @Test
    @DisplayName("create() defaults to DRAFT status")
    void create_defaultsToDraft() {
        assertThat(freshTender().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("create() rejects a blank name")
    void create_rejectsBlankName() {
        assertThatThrownBy(() -> Tender.create(TENANT, "TND-00001", "", null, null, null, null, null,
                null, null, null, USER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the full happy-path lifecycle DRAFT through AWARDED is reachable")
    void fullLifecycle_draftToAwarded() {
        Tender t = freshTender();

        t.transitionTo("IN_PREPARATION", USER);
        t.transitionTo("INTERNAL_REVIEW", USER);
        t.transitionTo("READY_TO_SUBMIT", USER);
        t.transitionTo("SUBMITTED", USER);
        assertThat(t.isSubmitted()).isTrue();
        assertThat(t.getSubmittedAt()).isNotNull();

        t.transitionTo("SHORTLISTED", USER);
        t.recordOutcome("AWARDED", "Best technical score", new BigDecimal("12100000"), USER);

        assertThat(t.getStatus()).isEqualTo("AWARDED");
        assertThat(t.getAwardedValue()).isEqualByComparingTo("12100000");
        assertThat(t.getOutcomeReason()).isEqualTo("Best technical score");
    }

    @Test
    @DisplayName("cannot jump straight from DRAFT to SUBMITTED, skipping preparation and review")
    void cannotSkipStatesInLifecycle() {
        Tender t = freshTender();

        assertThatThrownBy(() -> t.transitionTo("SUBMITTED", USER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cannot transition out of a terminal AWARDED state")
    void cannotTransitionOutOfAwarded() {
        Tender t = freshTender();
        t.transitionTo("IN_PREPARATION", USER);
        t.transitionTo("INTERNAL_REVIEW", USER);
        t.transitionTo("READY_TO_SUBMIT", USER);
        t.transitionTo("SUBMITTED", USER);
        t.recordOutcome("AWARDED", null, new BigDecimal("1000"), USER);

        assertThatThrownBy(() -> t.transitionTo("IN_PREPARATION", USER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("WITHDRAWN is reachable from any pre-submission state")
    void withdrawnReachableFromEarlyStates() {
        Tender t = freshTender();
        t.transitionTo("WITHDRAWN", USER); // straight from DRAFT
        assertThat(t.getStatus()).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("recordOutcome validates the outcome is actually reachable, same as transitionTo")
    void recordOutcome_validatesReachability() {
        Tender t = freshTender(); // still DRAFT

        assertThatThrownBy(() -> t.recordOutcome("AWARDED", "premature", new BigDecimal("1000"), USER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a tender not yet submitted reports isSubmitted() false")
    void notSubmitted_reportsFalse() {
        assertThat(freshTender().isSubmitted()).isFalse();
    }
}
