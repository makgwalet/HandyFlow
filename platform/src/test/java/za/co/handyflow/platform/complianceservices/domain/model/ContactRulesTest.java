package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PERSON = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Test
    void emptyOrOversizeTextRefused() {
        assertThatThrownBy(() -> ContactRules.subject("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ContactRules.body("x".repeat(4001))).isInstanceOf(IllegalArgumentException.class);
        assertThat(ContactRules.subject("  Hello ").equals("Hello")).isTrue();
    }

    @Test
    void fiveOpenIsTheLimit() {
        ContactRules.checkCanRaise(4);
        assertThatThrownBy(() -> ContactRules.checkCanRaise(5)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void statusFollowsWhoWrote() {
        assertThat(ContactRules.statusAfter("ANSWERED", "CLIENT").equals("OPEN")).isTrue();
        assertThat(ContactRules.statusAfter("OPEN", "STAFF").equals("ANSWERED")).isTrue();
        assertThatThrownBy(() -> ContactRules.statusAfter("CLOSED", "CLIENT")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void threadLengthCapped() {
        ContactRules.checkThreadLength(59);
        assertThatThrownBy(() -> ContactRules.checkThreadLength(60)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closeNeedsTenCharacters() {
        assertThatThrownBy(() -> ContactRules.closeNote("too short")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ContactRules.closeNote("Received by email").length() > 9).isTrue();
    }

    @Test
    void queryBecomesMediumAfterTwoDays() {
        Instant today = TODAY.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        assertThat(ContactRules.queryItem(TODAY, CLIENT, "A", "S", today).severity().equals(WorkQueueRules.LOW)).isTrue();
        assertThat(ContactRules.queryItem(TODAY, CLIENT, "A", "S", today.minusSeconds(3 * 86400)).severity().equals(WorkQueueRules.MEDIUM)).isTrue();
    }

    @Test
    void requestSeverityByDueDate() {
        assertThat(ContactRules.requestItem(TODAY, CLIENT, "A", "T", TODAY.plusDays(3)).severity().equals(WorkQueueRules.LOW)).isTrue();
        assertThat(ContactRules.requestItem(TODAY, CLIENT, "A", "T", TODAY).severity().equals(WorkQueueRules.MEDIUM)).isTrue();
        assertThat(ContactRules.requestItem(TODAY, CLIENT, "A", "T", TODAY.minusDays(8)).severity().equals(WorkQueueRules.HIGH)).isTrue();
        assertThat(ContactRules.requestItem(TODAY, CLIENT, "A", "T", null).severity().equals(WorkQueueRules.LOW)).isTrue();
    }

    @Test
    void pastDueDateRefused() {
        assertThatThrownBy(() -> ContactRules.dueDate(TODAY.minusDays(1), TODAY)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ContactRules.dueDate(null, TODAY) == null).isTrue();
    }

    @Test
    void queryLifecycle() {
        CompliancePortalQuery q = CompliancePortalQuery.raise(TENANT, CLIENT, " Subject ", PERSON, "Pat", 0);
        assertThat(q.getSubject().equals("Subject")).isTrue();
        q.messageAdded("CLIENT");
        q.messageAdded("STAFF");
        assertThat(q.getStatus().equals("ANSWERED")).isTrue();
        assertThat(q.getMessageCount() == 2).isTrue();
        q.close(PERSON);
        assertThat(q.getStatus().equals("CLOSED")).isTrue();
        assertThatThrownBy(() -> q.messageAdded("CLIENT")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CompliancePortalQuery.raise(TENANT, CLIENT, "S", PERSON, "Pat", 5)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void staffMessageStoresNoName() {
        CompliancePortalMessage m = CompliancePortalMessage.of(TENANT, UUID.randomUUID(), "STAFF", PERSON, "Should not be kept", "Hi");
        assertThat(m.getAuthorName() == null).isTrue();
        CompliancePortalMessage c = CompliancePortalMessage.of(TENANT, UUID.randomUUID(), "CLIENT", PERSON, "Pat", "Hi");
        assertThat(c.getAuthorName().equals("Pat")).isTrue();
    }

    @Test
    void requestLifecycle() {
        ComplianceDocumentRequest r = ComplianceDocumentRequest.create(TENANT, CLIENT, "Bank statements", null, "BANK", TODAY.plusDays(5), TODAY, PERSON);
        assertThat(r.isLive()).isTrue();
        assertThatThrownBy(() -> r.close(PERSON, "short")).isInstanceOf(IllegalArgumentException.class);
        r.close(PERSON, "Received by email on the 26th");
        assertThat(r.isLive()).isFalse();
        assertThatThrownBy(() -> r.cancel(PERSON, "Not needed any more")).isInstanceOf(IllegalStateException.class);
    }
}
