package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.identity.domain.model.User;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.application.internal.AuditInvestigationService.Actor;
import za.co.handyflow.platform.internalaudit.domain.model.*;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditInvestigationServiceTest {
    AuditInvestigationRepository cases = mock(AuditInvestigationRepository.class);
    AuditInvestigationMemberRepository members = mock(AuditInvestigationMemberRepository.class);
    AuditInvestigationLogRepository log = mock(AuditInvestigationLogRepository.class);
    AuditInvestigationEvidenceRepository evidence = mock(AuditInvestigationEvidenceRepository.class);
    UserRepository users = mock(UserRepository.class);
    AuditInvestigationService service;
    UUID tenant = UUID.randomUUID(), lead = UUID.randomUUID(), inv = UUID.randomUUID(), observer = UUID.randomUUID(), outsider = UUID.randomUUID(), caseId;
    TenantId tid;
    AuditInvestigation theCase;
    List<AuditInvestigationMember> roster = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new AuditInvestigationService(cases, members, log, evidence, users);
        tid = TenantId.of(tenant);
        theCase = AuditInvestigation.open(tenant, "INV-0001", "Till shortages", null, lead);
        caseId = theCase.getId();
        roster.clear();
        roster.add(AuditInvestigationMember.of(tenant, caseId, lead, "Lead", "LEAD", lead));
        roster.add(AuditInvestigationMember.of(tenant, caseId, inv, "Inv", "INVESTIGATOR", lead));
        roster.add(AuditInvestigationMember.of(tenant, caseId, observer, "Obs", "OBSERVER", lead));
        for (var m : roster)
            when(members.findByTenantIdAndInvestigationIdAndUserIdAndRemovedAtIsNull(tenant, caseId, m.getUserId())).thenReturn(Optional.of(m));
        when(members.findByTenantIdAndInvestigationIdAndUserIdAndRemovedAtIsNull(tenant, caseId, outsider)).thenReturn(Optional.empty());
        when(members.findByTenantIdAndInvestigationId(tenant, caseId)).thenReturn(roster);
        when(cases.lockByTenantIdAndId(tenant, caseId)).thenReturn(Optional.of(theCase));
        when(log.findFirstByTenantIdAndInvestigationIdOrderBySeqDesc(tenant, caseId)).thenReturn(Optional.empty());
        when(log.findByTenantIdAndInvestigationIdOrderBySeqAsc(tenant, caseId)).thenReturn(List.of());
        when(log.save(any())).thenAnswer(i -> i.getArgument(0));
        when(evidence.findByTenantIdAndInvestigationIdOrderByRefAsc(tenant, caseId)).thenReturn(List.of());
    }

    Actor as(UUID id) { return new Actor(id, "N"); }

    @Test
    void aPersonNotOnTheCaseIsToldItDoesNotExist_forEveryKindOfCall() {
        var o = as(outsider);
        assertThrows(ResourceNotFoundException.class, () -> service.get(tid, o, caseId));
        assertThrows(ResourceNotFoundException.class, () -> service.addNote(tid, o, caseId, "A note that is long enough"));
        assertThrows(ResourceNotFoundException.class, () -> service.verify(tid, o, caseId));
        assertThrows(ResourceNotFoundException.class, () -> service.close(tid, o, caseId, "Closing it for good reason"));
        assertThrows(ResourceNotFoundException.class, () -> service.removeMember(tid, o, caseId, lead));
        verify(log, never()).save(any());
    }

    @Test
    void listShowsOnlyTheCallersOwnCases() {
        when(members.findByTenantIdAndUserIdAndRemovedAtIsNull(tenant, outsider)).thenReturn(List.of());
        assertTrue(service.list(tid, as(outsider)).isEmpty());
        verify(cases, never()).findAllByIds(any(), any());
    }

    @Test
    void anObserverCanReadButNotWrite() {
        var o = as(observer);
        service.get(tid, o, caseId);
        var e = assertThrows(HandyFlowException.class, () -> service.addNote(tid, o, caseId, "A note that is long enough"));
        assertEquals("INV_READ_ONLY", e.getErrorCode());
        assertEquals("INV_READ_ONLY", assertThrows(HandyFlowException.class, () -> service.collectEvidence(tid, o, caseId, "Till roll for the third", null)).getErrorCode());
    }

    @Test
    void anInvestigatorCanWriteButNotRunTheCase() {
        var i = as(inv);
        service.addNote(tid, i, caseId, "Spoke to the cashier on duty.");
        assertEquals("INV_LEAD_ONLY", assertThrows(HandyFlowException.class, () -> service.close(tid, i, caseId, "Closing it for good reason")).getErrorCode());
        assertEquals("INV_LEAD_ONLY", assertThrows(HandyFlowException.class, () -> service.addMember(tid, i, caseId, outsider, "OBSERVER")).getErrorCode());
        assertEquals("INV_LEAD_ONLY", assertThrows(HandyFlowException.class, () -> service.removeMember(tid, i, caseId, observer)).getErrorCode());
    }

    @Test
    void everyWriteAddsOneChainedLine_andShortNotesAreRefused() {
        service.addNote(tid, as(lead), caseId, "Spoke to the cashier on duty.");
        verify(log).save(argThat(e -> e.getAction().equals("NOTE") && e.getSeq() == 1 && e.getPrevHash().equals(InvestigationChain.GENESIS)));
        assertEquals("INV_INVALID", assertThrows(HandyFlowException.class, () -> service.addNote(tid, as(lead), caseId, "ok")).getErrorCode());
    }

    @Test
    void theLastLeadCannotLeave() {
        var e = assertThrows(HandyFlowException.class, () -> service.removeMember(tid, as(lead), caseId, lead));
        assertEquals("INV_STATE", e.getErrorCode());
        assertTrue(roster.get(0).isActive());
    }

    @Test
    void anyoneCanTakeThemselvesOff() {
        when(members.findByTenantIdAndInvestigationIdAndUserIdAndRemovedAtIsNull(tenant, caseId, observer)).thenReturn(Optional.of(roster.get(2)), Optional.of(roster.get(2)), Optional.empty());
        var v = service.removeMember(tid, as(observer), caseId, observer);
        assertNull(v);
        assertFalse(roster.get(2).isActive());
    }

    @Test
    void aClosedCaseTakesNoMoreEntries() {
        service.close(tid, as(lead), caseId, "Concluded; referred to HR.");
        assertTrue(theCase.isClosed());
        assertEquals("INV_STATE", assertThrows(HandyFlowException.class, () -> service.addNote(tid, as(lead), caseId, "A late note that is long enough")).getErrorCode());
    }

    @Test
    void closingNeedsAReason() {
        assertEquals("INV_INVALID", assertThrows(HandyFlowException.class, () -> service.close(tid, as(lead), caseId, "done")).getErrorCode());
        assertFalse(theCase.isClosed());
    }

    @Test
    void evidenceCanOnlyGoToALeadOrInvestigatorOnTheCase() {
        var ev = AuditInvestigationEvidence.collect(tenant, caseId, "EV-001", "Till roll for 3 October", null, lead, "Lead");
        when(evidence.findByTenantIdAndInvestigationIdAndId(tenant, caseId, ev.getId())).thenReturn(Optional.of(ev));
        assertEquals("INV_INVALID", assertThrows(HandyFlowException.class, () -> service.transferEvidence(tid, as(lead), caseId, ev.getId(), observer, "Handing over for review")).getErrorCode());
        assertEquals("INV_INVALID", assertThrows(HandyFlowException.class, () -> service.transferEvidence(tid, as(lead), caseId, ev.getId(), outsider, "Handing over for review")).getErrorCode());
        service.transferEvidence(tid, as(lead), caseId, ev.getId(), inv, "Handing over for review");
        assertEquals(inv, ev.getHolderId());
        verify(log).save(argThat(e -> e.getAction().equals("EVIDENCE_TRANSFERRED")));
    }

    @Test
    void aTransferNeedsAReason() {
        var ev = AuditInvestigationEvidence.collect(tenant, caseId, "EV-001", "Till roll for 3 October", null, lead, "Lead");
        when(evidence.findByTenantIdAndInvestigationIdAndId(tenant, caseId, ev.getId())).thenReturn(Optional.of(ev));
        assertEquals("INV_INVALID", assertThrows(HandyFlowException.class, () -> service.transferEvidence(tid, as(lead), caseId, ev.getId(), inv, "x")).getErrorCode());
        assertEquals(lead, ev.getHolderId());
    }

    @Test
    void viewsAreLoggedOncePerQuarterHourPerPerson() {
        var recent = AuditInvestigationLogEntry.next(tenant, caseId, null, Instant.now().minusSeconds(60), inv, "Inv", "VIEWED", null);
        when(log.findFirstByTenantIdAndInvestigationIdAndActorIdAndActionOrderBySeqDesc(tenant, caseId, inv, "VIEWED")).thenReturn(Optional.of(recent));
        service.get(tid, as(inv), caseId);
        verify(log, never()).save(any());
        var old = AuditInvestigationLogEntry.next(tenant, caseId, null, Instant.now().minusSeconds(3600), inv, "Inv", "VIEWED", null);
        when(log.findFirstByTenantIdAndInvestigationIdAndActorIdAndActionOrderBySeqDesc(tenant, caseId, inv, "VIEWED")).thenReturn(Optional.of(old));
        service.get(tid, as(inv), caseId);
        verify(log).save(argThat(e -> e.getAction().equals("VIEWED")));
    }

    @Test
    void addingSomeoneNeedsARealUserOnTheCompany_andNotTwice() {
        var u = mock(User.class);
        when(u.getFirstName()).thenReturn("New"); when(u.getLastName()).thenReturn("Person");
        UUID fresh = UUID.randomUUID();
        when(users.findByIdAndTenantId(fresh, tid)).thenReturn(Optional.of(u));
        when(members.findByTenantIdAndInvestigationIdAndUserIdAndRemovedAtIsNull(tenant, caseId, fresh)).thenReturn(Optional.empty());
        service.addMember(tid, as(lead), caseId, fresh, "observer");
        verify(members).save(argThat(m -> m.getRole().equals("OBSERVER") && m.getUserName().equals("New Person")));
        assertThrows(ResourceNotFoundException.class, () -> service.addMember(tid, as(lead), caseId, UUID.randomUUID(), "OBSERVER"));
        assertEquals("INV_MEMBER_EXISTS", assertThrows(HandyFlowException.class, () -> service.addMember(tid, as(lead), caseId, inv, "OBSERVER")).getErrorCode());
    }
}
