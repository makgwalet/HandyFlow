package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mirrors compliancetender.TenderTest almost exactly — the lifecycle is deliberately identical, only the entity is parallel. */
class ClientTenderTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private ClientTender freshTender() {
        return ClientTender.create(TENANT, CLIENT_ID, "CTND-00001", "Municipal Road Upgrade", "City of Cape Town",
                "CCT-2026-045", null, null, null, new BigDecimal("12500000"), "Construction",
                "cidb Grade 6GB", USER);
    }

    @Test
    @DisplayName("create() defaults to DRAFT status")
    void create_defaultsToDraft() {
        assertThat(freshTender().getStatus()).isEqualTo("DRAFT");
        assertThat(freshTender().getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("create() rejects a blank name")
    void create_rejectsBlankName() {
        assertThatThrownBy(() -> ClientTender.create(TENANT, CLIENT_ID, "CTND-00001", "", null, null, null, null,
                null, null, null, null, USER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the full happy-path lifecycle DRAFT through AWARDED is reachable")
    void fullLifecycle_draftToAwarded() {
        ClientTender t = freshTender();

        t.transitionTo("IN_PREPARATION", USER);
        t.transitionTo("INTERNAL_REVIEW", USER);
        t.transitionTo("READY_TO_SUBMIT", USER);
        t.transitionTo("SUBMITTED", USER);
        assertThat(t.isSubmitted()).isTrue();

        t.transitionTo("SHORTLISTED", USER);
        t.recordOutcome("AWARDED", "Best technical score", new BigDecimal("12100000"), USER);

        assertThat(t.getStatus()).isEqualTo("AWARDED");
        assertThat(t.getAwardedValue()).isEqualByComparingTo("12100000");
    }

    @Test
    @DisplayName("cannot jump straight from DRAFT to SUBMITTED, skipping preparation and review")
    void cannotSkipStatesInLifecycle() {
        ClientTender t = freshTender();

        assertThatThrownBy(() -> t.transitionTo("SUBMITTED", USER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("WITHDRAWN is reachable from any pre-submission state")
    void withdrawnReachableFromEarlyStates() {
        ClientTender t = freshTender();
        t.transitionTo("WITHDRAWN", USER);
        assertThat(t.getStatus()).isEqualTo("WITHDRAWN");
    }
}
