package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.CounterpartyCommentInput;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.ProposalInput;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.StaffCommentRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Staff are told when a counterparty acts: published by the service, delivered after commit, throttled, and never for staff's own actions. */
class ContractNegotiationNotifierTest {

    static final String P1 = "<p>Payment is due in 30 days.</p>";

    // ── what the service publishes ───────────────────────────────────────────

    NegotiationHarness h;
    TenantId tenant = TenantId.generate();
    UUID staff = UUID.randomUUID();
    Contract c;

    @BeforeEach
    void setUp() {
        h = new NegotiationHarness();
        c = h.contract(tenant, "Supply", P1);
        h.inTxDo(() -> h.service.open(tenant, c.getId(), LocalDate.now().plusDays(5), staff));
        clearInvocations(h.publisher);
    }

    private NegotiationActivity published() {
        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(h.publisher).publishEvent(cap.capture());
        return (NegotiationActivity) cap.getValue();
    }

    @Test
    void aProposalPublishesOneActivityNamingTheParty() {
        h.inTx(() -> h.service.submitProposal(tenant, c.getId(), h.party(c, 0).getId(), new ProposalInput("DELETE", 0, P1, null, null), "1.1.1.1"));
        NegotiationActivity a = published();
        assertEquals(NegotiationActivity.PROPOSAL, a.kind());
        assertEquals(tenant.getValue(), a.tenantId());
        assertEquals(c.getId(), a.contractId());
        assertEquals(h.party(c, 0).getFullName(), a.partyName());
    }

    @Test
    void aCounterpartyCommentPublishesAnActivity() {
        h.inTx(() -> h.service.counterpartyComment(tenant, c.getId(), h.party(c, 0).getId(), new CounterpartyCommentInput("question", null, null, null, null), null));
        assertEquals(NegotiationActivity.COMMENT, published().kind());
    }

    @Test
    void aRefusedProposalPublishesNothing() {
        assertThrows(IllegalStateException.class, () -> h.inTx(() -> h.service.submitProposal(tenant, c.getId(), h.party(c, 0).getId(),
                new ProposalInput("DELETE", 0, "<p>Not in the contract.</p>", null, null), null)));
        verifyNoInteractions(h.publisher);
    }

    @Test
    void staffsOwnActionsPublishNothing() {
        h.inTx(() -> h.service.staffComment(tenant, c.getId(), new StaffCommentRequest("note", null, null, null, null, null, false), staff));
        verifyNoInteractions(h.publisher);
    }

    // ── what the listener does with it ───────────────────────────────────────

    ContractRepository contractRepo = mock(ContractRepository.class);
    EmailService email = mock(EmailService.class);
    TenantAdminRecipients recipients = mock(TenantAdminRecipients.class);
    Contract contract = mock(Contract.class);
    UUID tenantId = UUID.randomUUID(), contractId = UUID.randomUUID(), creator = UUID.randomUUID();
    MutableClock clock = new MutableClock();
    ContractNegotiationNotifier notifier;

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-19T07:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }

    private ContractNegotiationNotifier notifier() {
        when(contract.getTenantId()).thenReturn(tenantId);
        when(contract.getTitle()).thenReturn("Supply <agreement>");
        when(contract.getContractNumber()).thenReturn("CTR-9");
        when(contract.getCreatedBy()).thenReturn(creator);
        when(contractRepo.findById(contractId)).thenReturn(Optional.of(contract));
        when(recipients.resolveUsers(any(TenantId.class), any())).thenReturn(List.of(new Recipient(creator, "Creator", "creator@x.co.za", null)));
        when(recipients.resolveTenantAdmins(any(UUID.class))).thenReturn(List.of(new Recipient(UUID.randomUUID(), "Admin", "admin@x.co.za", null)));
        return new ContractNegotiationNotifier(contractRepo, email, recipients, clock);
    }

    private NegotiationActivity activity(String kind) { return new NegotiationActivity(tenantId, contractId, kind, "Acme <Ltd>"); }

    @Test
    void emailsTheCreatorWithTheCounterpartysNameEscapedAndSaysTheContractIsUnchanged() {
        notifier().on(activity(NegotiationActivity.PROPOSAL));
        verify(email).send(eq("creator@x.co.za"), eq("New proposal on Supply <agreement>"), argThat((String body) ->
                body.contains("Acme &lt;Ltd&gt; has proposed a change to contract CTR-9 (Supply &lt;agreement&gt;)") && body.contains("has not changed")));
    }

    @Test
    void aCommentIsWordedAsAComment() {
        notifier().on(activity(NegotiationActivity.COMMENT));
        verify(email).send(anyString(), startsWith("New comment on"), contains("commented on"));
    }

    @Test
    void fallsBackToTheTenantAdministrators() {
        ContractNegotiationNotifier n = notifier();
        when(recipients.resolveUsers(any(TenantId.class), any())).thenReturn(List.of());
        n.on(activity(NegotiationActivity.PROPOSAL));
        verify(email).send(eq("admin@x.co.za"), anyString(), anyString());
    }

    @Test
    void aSecondActivityWithinTenMinutesIsQuietAndOneAfterwardsIsNot() {
        ContractNegotiationNotifier n = notifier();
        n.on(activity(NegotiationActivity.PROPOSAL));
        clock.now = clock.now.plus(Duration.ofMinutes(9));
        n.on(activity(NegotiationActivity.PROPOSAL));
        verify(email, times(1)).send(anyString(), anyString(), anyString());
        clock.now = clock.now.plus(Duration.ofMinutes(2));
        n.on(activity(NegotiationActivity.PROPOSAL));
        verify(email, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void theThrottleIsPerContract() {
        ContractNegotiationNotifier n = notifier();
        UUID other = UUID.randomUUID();
        when(contractRepo.findById(other)).thenReturn(Optional.of(contract));
        n.on(activity(NegotiationActivity.PROPOSAL));
        n.on(new NegotiationActivity(tenantId, other, NegotiationActivity.PROPOSAL, "Beta"));
        verify(email, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void aContractOfAnotherTenantIsNeverMailed() {
        ContractNegotiationNotifier n = notifier();
        n.on(new NegotiationActivity(UUID.randomUUID(), contractId, NegotiationActivity.PROPOSAL, "Acme"));
        verifyNoInteractions(email);
    }

    @Test
    void aMailFailureIsSwallowedAndDoesNotStartTheQuietPeriod() {
        ContractNegotiationNotifier n = notifier();
        doThrow(new RuntimeException("smtp down")).doNothing().when(email).send(anyString(), anyString(), anyString());
        assertDoesNotThrow(() -> n.on(activity(NegotiationActivity.PROPOSAL)));
        n.on(activity(NegotiationActivity.PROPOSAL));
        verify(email, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void withNoRecipientNothingIsSentAndTheNextActivityTriesAgain() {
        ContractNegotiationNotifier n = notifier();
        when(recipients.resolveUsers(any(TenantId.class), any())).thenReturn(List.of());
        when(recipients.resolveTenantAdmins(any(UUID.class))).thenReturn(List.of());
        n.on(activity(NegotiationActivity.PROPOSAL));
        verifyNoInteractions(email);
    }
}
