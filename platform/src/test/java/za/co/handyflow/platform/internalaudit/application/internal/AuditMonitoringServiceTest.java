package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accounting.domain.model.AccJournalEntry;
import za.co.handyflow.platform.accounting.domain.repository.AccBankAccountRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccBankTransactionRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccJournalEntryRepository;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.application.internal.AuditMonitoringService.Actor;
import za.co.handyflow.platform.internalaudit.domain.model.AuditMonitorAlert;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditFindingRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditMonitorAlertRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditMonitoringServiceTest {
    AuditMonitorAlertRepository alerts = mock(AuditMonitorAlertRepository.class);
    AccJournalEntryRepository journals = mock(AccJournalEntryRepository.class);
    AccBankAccountRepository bankAccounts = mock(AccBankAccountRepository.class);
    AccBankTransactionRepository bankTx = mock(AccBankTransactionRepository.class);
    UserRepository users = mock(UserRepository.class);
    AuditFindingRepository findings = mock(AuditFindingRepository.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    AuditMonitoringService service;
    UUID tenant = UUID.randomUUID(), ann = UUID.randomUUID(), bob = UUID.randomUUID(), rev = UUID.randomUUID();
    TenantId tid;
    LocalDate today = LocalDate.parse("2026-10-14");
    List<AuditMonitorAlert> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new AuditMonitoringService(alerts, journals, bankAccounts, bankTx, users, findings, trail);
        tid = TenantId.of(tenant);
        stored.clear();
        when(alerts.save(any())).thenAnswer(i -> { var a = (AuditMonitorAlert) i.getArgument(0); if (!stored.contains(a)) stored.add(a); return a; });
        when(alerts.findByTenantIdAndFingerprintIn(eq(tenant), any())).thenAnswer(i -> {
            Collection<String> fps = i.getArgument(1);
            return stored.stream().filter(a -> fps.contains(a.getFingerprint())).toList();
        });
        when(alerts.findByTenantIdOrderByFirstSeenDesc(tenant)).thenAnswer(i -> new ArrayList<>(stored));
        when(bankAccounts.findAllActive(tid)).thenReturn(List.of());
        when(users.findByTenantIdOrderByLastNameAscFirstNameAsc(tid)).thenReturn(List.of());
    }

    private AccJournalEntry journal(String number, String ref) {
        var j = mock(AccJournalEntry.class);
        when(j.getId()).thenReturn(UUID.randomUUID());
        when(j.getEntryNumber()).thenReturn(number);
        when(j.getStatus()).thenReturn("POSTED");
        when(j.getEntryDate()).thenReturn(LocalDate.parse("2026-10-01"));
        when(j.getPostedAt()).thenReturn(Instant.parse("2026-10-02T10:00:00Z"));
        when(j.getDescription()).thenReturn("Rent");
        when(j.getReference()).thenReturn(ref);
        when(j.getEntryType()).thenReturn("MANUAL");
        when(j.getTotalDebit()).thenReturn(new BigDecimal("1500.00"));
        when(j.getCreatedBy()).thenReturn(ann);
        when(j.getPostedBy()).thenReturn(bob);
        return j;
    }

    private void twoSameJournals() {
        var a = journal("J1", "INV-77");
        var b = journal("J2", "INV-77");
        when(journals.findPostedInRange(eq(tid), any(), any())).thenReturn(List.of(a, b));
    }

    @Test
    void aScanFilesOneAlertForAPattern_andRunningAgainFilesNothingNew() {
        twoSameJournals();
        var first = service.scan(tid, today, null);
        assertEquals(1, first.newAlerts());
        assertEquals("NEW", stored.get(0).getStatus());
        var second = service.scan(tid, today, null);
        assertEquals(0, second.newAlerts());
        assertEquals(1, second.seenAgain());
        assertEquals(1, stored.size());
    }

    @Test
    void aDismissedPatternIsNotFiledAgain() {
        twoSameJournals();
        service.scan(tid, today, null);
        stored.get(0).review("DISMISSED", "Rent split on purpose.", null, UUID.randomUUID(), "Rev");
        service.scan(tid, today, null);
        assertEquals(1, stored.size());
        assertEquals("DISMISSED", stored.get(0).getStatus());
    }

    @Test
    void aScanNeverChangesAJournal() {
        twoSameJournals();
        service.scan(tid, today, null);
        verify(journals, never()).save(any());
        verify(journals, never()).saveAll(any());
    }

    @Test
    void reviewingNeedsARealAlert_andANoteToClose() {
        twoSameJournals();
        service.scan(tid, today, null);
        UUID id = stored.get(0).getId();
        when(alerts.findByTenantIdAndId(tenant, id)).thenReturn(Optional.of(stored.get(0)));
        assertThrows(ResourceNotFoundException.class, () -> service.review(tid, new Actor(rev, "Rev"), UUID.randomUUID(), "DISMISSED", "A long enough note", null));
        assertEquals("MON_INVALID", assertThrows(HandyFlowException.class, () -> service.review(tid, new Actor(rev, "Rev"), id, "DISMISSED", "no", null)).getErrorCode());
        // Ann made the journals, so she cannot review an alert about them
        assertEquals("MON_STATE", assertThrows(HandyFlowException.class, () -> service.review(tid, new Actor(ann, "Ann"), id, "DISMISSED", "It is all fine really", null)).getErrorCode());
        service.review(tid, new Actor(rev, "Rev"), id, "DISMISSED", "Rent split on purpose.", null);
        assertEquals("DISMISSED", stored.get(0).getStatus());
        verify(trail).record(eq(tid), eq("AUDIT_MONITOR_REVIEWED"), eq("AUDIT_MONITOR_ALERT"), eq(id), any(), any());
    }

    @Test
    void anUnknownFindingCannotBeLinked() {
        twoSameJournals();
        service.scan(tid, today, null);
        UUID id = stored.get(0).getId();
        when(alerts.findByTenantIdAndId(tenant, id)).thenReturn(Optional.of(stored.get(0)));
        when(findings.findByTenantAndId(eq(tenant), any())).thenReturn(Optional.empty());
        assertEquals("MON_INVALID", assertThrows(HandyFlowException.class, () -> service.review(tid, new Actor(rev, "Rev"), id, "ESCALATED", "Needs the team to look", UUID.randomUUID())).getErrorCode());
    }

    @Test
    void countsSeparateOpenFromClosed() {
        twoSameJournals();
        service.scan(tid, today, null);
        var c = service.list(tid).counts();
        assertEquals(1, c.open());
        assertEquals(1, c.medium());
        stored.get(0).review("DISMISSED", "Rent split on purpose.", null, UUID.randomUUID(), "Rev");
        c = service.list(tid).counts();
        assertEquals(0, c.open());
        assertEquals(1, c.dismissed());
    }
}
