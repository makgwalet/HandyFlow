package za.co.handyflow.platform.contracting.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiation;
import za.co.handyflow.platform.contracting.domain.model.ContractNegotiationProposal;
import za.co.handyflow.platform.contracting.domain.repository.ContractNegotiationProposalRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractNegotiationRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractNegotiationSessionRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContractNegotiationSchedulerTest {

    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    LocalDate today = LocalDate.now(ZONE);
    Instant yesterday = today.minusDays(1).atStartOfDay(ZONE).toInstant().plusSeconds(3600);
    Instant justNow = Instant.now();

    ContractNegotiationSessionRepository sessionRepo = mock(ContractNegotiationSessionRepository.class);
    ContractNegotiationRepository negotiationRepo = mock(ContractNegotiationRepository.class);
    ContractNegotiationProposalRepository proposalRepo = mock(ContractNegotiationProposalRepository.class);
    ContractRepository contractRepo = mock(ContractRepository.class);
    EmailService email = mock(EmailService.class);
    TenantAdminRecipients recipients = mock(TenantAdminRecipients.class);
    ContractNegotiationScheduler scheduler;

    UUID tenant = UUID.randomUUID(), creator = UUID.randomUUID();
    Contract contract = mock(Contract.class);

    @BeforeEach
    void setUp() {
        scheduler = new ContractNegotiationScheduler(sessionRepo, negotiationRepo, proposalRepo, contractRepo, email, recipients);
        when(contract.getId()).thenReturn(UUID.randomUUID());
        when(contract.getTitle()).thenReturn("Supply <agreement>");
        when(contract.getContractNumber()).thenReturn("CTR-9");
        when(contract.getCreatedBy()).thenReturn(creator);
        when(contractRepo.findAllById(any())).thenReturn(List.of(contract));
        when(recipients.resolveUsers(any(TenantId.class), any())).thenReturn(List.of(new Recipient(creator, "Creator", "creator@x.co.za", null)));
        when(recipients.resolveTenantAdmins(any(UUID.class))).thenReturn(List.of(new Recipient(UUID.randomUUID(), "Admin", "admin@x.co.za", null)));
    }

    private ContractNegotiation negotiation(LocalDate deadline) {
        ContractNegotiation n = ContractNegotiation.open(tenant, contract.getId(), 1, deadline, creator, today);
        when(negotiationRepo.findByStatusIn(any())).thenReturn(List.of(n));
        return n;
    }

    private void pendingSince(Instant created) {
        ContractNegotiationProposal p = mock(ContractNegotiationProposal.class);
        when(p.getCreatedAt()).thenReturn(created);
        when(proposalRepo.findByTenantIdAndContractIdAndStatus(any(), any(), eq(ContractNegotiationProposal.PENDING))).thenReturn(List.of(p));
    }

    // ── the rule ─────────────────────────────────────────────────────────────

    @Test
    void aDeadlineWithinTwoDaysRemindsOncePerDay() {
        ContractNegotiation n = negotiation(today.plusDays(2));
        assertEquals("DEADLINE_SOON", n.reminderReason(today, null, ZONE));
        n.markReminded(today);
        assertNull(n.reminderReason(today, null, ZONE));
        assertEquals("DEADLINE_SOON", n.reminderReason(today.plusDays(1), null, ZONE));
    }

    @Test
    void aDistantDeadlineAndNothingWaitingRemindsNoOne() {
        assertNull(negotiation(today.plusDays(3)).reminderReason(today, null, ZONE));
        assertNull(negotiation(null).reminderReason(today, null, ZONE));
    }

    @Test
    void aPassedDeadlineRemindsOnceAWeek() {
        ContractNegotiation n = ContractNegotiation.open(tenant, UUID.randomUUID(), 1, today.plusDays(1), creator, today);
        LocalDate later = today.plusDays(3);
        assertEquals("DEADLINE_PASSED", n.reminderReason(later, null, ZONE));
        n.markReminded(later);
        assertNull(n.reminderReason(later.plusDays(6), null, ZONE));
        assertEquals("DEADLINE_PASSED", n.reminderReason(later.plusDays(7), null, ZONE));
    }

    @Test
    void proposalsWaitingSinceBeforeTodayRemindDailyButNewOnesWaitForTomorrow() {
        ContractNegotiation n = negotiation(null);
        assertEquals("PENDING", n.reminderReason(today, yesterday, ZONE));
        assertNull(n.reminderReason(today, justNow, ZONE));
        n.markReminded(today);
        assertNull(n.reminderReason(today, yesterday, ZONE));
    }

    @Test
    void aReadyOrClosedNegotiationIsNeverRemindedAbout() {
        ContractNegotiation n = negotiation(today.plusDays(1));
        n.close("done", creator);
        assertNull(n.reminderReason(today, yesterday, ZONE));
    }

    // ── the job ──────────────────────────────────────────────────────────────

    @Test
    void emailsTheContractCreatorAndRecordsTheDay() {
        ContractNegotiation n = negotiation(today.plusDays(1));
        scheduler.sendReminders();
        verify(email).send(eq("creator@x.co.za"), contains("deadline close"), contains("Response deadline"));
        verify(negotiationRepo).save(n);
        assertEquals(today, n.getLastRemindedOn());
    }

    @Test
    void runningTwiceInADaySendsOnce() {
        negotiation(today.plusDays(1));
        scheduler.sendReminders();
        scheduler.sendReminders();
        verify(email, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void fallsBackToTheTenantAdministratorsWhenTheCreatorCannotBeResolved() {
        negotiation(today.plusDays(1));
        when(recipients.resolveUsers(any(TenantId.class), any())).thenReturn(List.of());
        scheduler.sendReminders();
        verify(email).send(eq("admin@x.co.za"), anyString(), anyString());
    }

    @Test
    void withNoRecipientNothingIsSentAndTheDayIsNotRecordedSoTomorrowTriesAgain() {
        ContractNegotiation n = negotiation(today.plusDays(1));
        when(recipients.resolveUsers(any(TenantId.class), any())).thenReturn(List.of());
        when(recipients.resolveTenantAdmins(any(UUID.class))).thenReturn(List.of());
        scheduler.sendReminders();
        verifyNoInteractions(email);
        assertNull(n.getLastRemindedOn());
    }

    @Test
    void waitingProposalsAreCountedInTheMessageAndTheContractTextIsEscaped() {
        negotiation(null);
        pendingSince(yesterday);
        scheduler.sendReminders();
        verify(email).send(eq("creator@x.co.za"), startsWith("Proposals waiting"), contains("1 proposal is waiting"));
        assertEquals("<p>Contract CTR-9 (Supply &lt;agreement&gt;) is being negotiated.</p>",
                ContractNegotiationScheduler.body("PENDING", ContractNegotiation.open(tenant, UUID.randomUUID(), 1, null, creator, today), contract, 0));
    }

    @Test
    void oneFailureDoesNotStopTheRest() {
        ContractNegotiation first = negotiation(today.plusDays(1));
        ContractNegotiation second = ContractNegotiation.open(tenant, contract.getId(), 1, today.plusDays(1), creator, today);
        when(negotiationRepo.findByStatusIn(any())).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("smtp down")).doNothing().when(email).send(anyString(), anyString(), anyString());
        scheduler.sendReminders();
        assertNull(first.getLastRemindedOn());
        assertEquals(today, second.getLastRemindedOn());
    }

    @Test
    void onlyActiveNegotiationsAreLoaded() {
        when(negotiationRepo.findByStatusIn(any())).thenReturn(List.of());
        scheduler.sendReminders();
        verify(negotiationRepo).findByStatusIn(Set.of("OPEN", "STAFF_REVIEW", "CHANGES_REQUIRED"));
        verifyNoInteractions(email);
    }

    @Test
    void expiredSessionsAreDeletedWithADayOfGrace() {
        when(sessionRepo.deleteExpired(any())).thenReturn(3);
        Instant before = Instant.now();
        scheduler.deleteExpiredSessions();
        verify(sessionRepo).deleteExpired(argThat(cutoff -> cutoff.isBefore(before.minusSeconds(23 * 3600)) && cutoff.isAfter(before.minusSeconds(25 * 3600))));
    }
}
