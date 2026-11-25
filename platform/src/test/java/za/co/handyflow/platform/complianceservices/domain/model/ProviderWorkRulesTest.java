package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderWorkRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final TenantId T = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));

    private ComplianceProviderAssignment proposed() {
        return ComplianceProviderAssignment.propose(T, UUID.randomUUID(), UUID.randomUUID(), "Fire audit", null, null, TODAY.plusDays(10), true, null, TODAY, ME);
    }

    @Test
    void cannotStartWithoutConflictDeclaration() {
        ComplianceProviderAssignment a = proposed();
        assertThatThrownBy(() -> a.activate(ME, true, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noConflictThenActivateNeedsUsableAccreditation() {
        ComplianceProviderAssignment a = proposed();
        a.declare(false, "No relationship with this provider", ME);
        assertThatThrownBy(() -> a.activate(ME, true, false)).isInstanceOf(IllegalStateException.class);
        a.activate(ME, true, true);
        assertThat(a.getStatus().equals("ACTIVE")).isTrue();
    }

    @Test
    void waivedAccreditationNeedsReason() {
        assertThatThrownBy(() -> ComplianceProviderAssignment.propose(T, UUID.randomUUID(), UUID.randomUUID(), "x", null, null, null, false, "short", TODAY, ME)).isInstanceOf(IllegalArgumentException.class);
        ComplianceProviderAssignment a = ComplianceProviderAssignment.propose(T, UUID.randomUUID(), UUID.randomUUID(), "x", null, null, null, false, "Provider is a sole practitioner, no body exists", TODAY, ME);
        a.declare(false, "No relationship with this provider", ME);
        a.activate(ME, true, false);
        assertThat(a.getStatus().equals("ACTIVE")).isTrue();
    }

    @Test
    void declaredConflictNeedsDifferentApprover() {
        ComplianceProviderAssignment a = proposed();
        a.declare(true, "Director is related to the provider", ME);
        assertThatThrownBy(() -> a.approveConflict(ME, "Approved after review")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> a.activate(ME, true, true)).isInstanceOf(IllegalStateException.class);
        a.approveConflict(OTHER, "Approved after review");
        a.activate(ME, true, true);
        assertThat(a.getStatus().equals("ACTIVE")).isTrue();
    }

    @Test
    void completionNeedsRatingNoteAndDeliverables() {
        ComplianceProviderAssignment a = proposed();
        a.declare(false, "No relationship with this provider", ME);
        a.activate(ME, true, true);
        assertThatThrownBy(() -> a.complete(ME, 6, "Done well and on time", true, TODAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.complete(ME, 4, "short", true, TODAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.complete(ME, 4, "Done well and on time", false, TODAY)).isInstanceOf(IllegalStateException.class);
        a.complete(ME, 4, "Done well and on time", true, TODAY);
        assertThat(a.getStatus().equals("COMPLETED")).isTrue();
        assertThat(Boolean.TRUE.equals(a.getOnTime())).isTrue();
    }

    @Test
    void reviewerMustDifferFromReceiver() {
        ComplianceProviderDeliverable d = ComplianceProviderDeliverable.expect(T, UUID.randomUUID(), "Report", null);
        d.received(ME);
        assertThatThrownBy(() -> d.review(true, ME, "Looks complete to me")).isInstanceOf(IllegalStateException.class);
        d.review(true, OTHER, "Looks complete to me");
        assertThat(d.getStatus().equals("ACCEPTED")).isTrue();
    }

    @Test
    void rejectedCanBeReceivedAgain() {
        ComplianceProviderDeliverable d = ComplianceProviderDeliverable.expect(T, UUID.randomUUID(), "Report", null);
        d.received(ME);
        d.review(false, OTHER, "Missing the signed page");
        d.received(ME);
        assertThat(d.getStatus().equals("RECEIVED")).isTrue();
    }

    @Test
    void deliverablesDoneOnlyWhenAcceptedOrWaived() {
        assertThat(ProviderWorkRules.deliverablesDone(List.of("ACCEPTED", "WAIVED"))).isTrue();
        assertThat(ProviderWorkRules.deliverablesDone(List.of("ACCEPTED", "RECEIVED"))).isFalse();
        assertThat(ProviderWorkRules.deliverablesDone(List.of("REJECTED"))).isFalse();
    }

    @Test
    void cancelNeedsReasonAndCannotRepeat() {
        ComplianceProviderAssignment a = proposed();
        assertThatThrownBy(() -> a.cancel(ME, "no")).isInstanceOf(IllegalArgumentException.class);
        a.cancel(ME, "Client withdrew the request");
        assertThatThrownBy(() -> a.cancel(ME, "Client withdrew the request")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void queueItems() {
        UUID c = UUID.randomUUID();
        assertThat(ProviderWorkRules.assignmentItem("P", "T", c, "C", "ACTIVE", "NONE", false, TODAY.minusDays(10), TODAY.minusDays(30), TODAY).severity().equals(WorkQueueRules.HIGH)).isTrue();
        assertThat(ProviderWorkRules.assignmentItem("P", "T", c, "C", "ACTIVE", "NONE", false, TODAY.plusDays(10), TODAY.minusDays(30), TODAY) == null).isTrue();
        assertThat(ProviderWorkRules.deliverableItem("P", "T", "D", c, "C", TODAY.minusDays(5), TODAY).severity().equals(WorkQueueRules.MEDIUM)).isTrue();
    }
}
