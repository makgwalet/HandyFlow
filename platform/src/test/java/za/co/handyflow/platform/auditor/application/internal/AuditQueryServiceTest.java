package za.co.handyflow.platform.auditor.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Actor;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Scope;
import za.co.handyflow.platform.auditor.domain.model.AuditQuery;
import za.co.handyflow.platform.auditor.domain.repository.AuditQueryRepository;
import za.co.handyflow.platform.auditor.dto.AuditQueryDtos.RaiseRequest;
import za.co.handyflow.platform.internalaudit.domain.model.AuditFinding;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditFindingRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuditQueryServiceTest {
    AuditQueryRepository repo = mock(AuditQueryRepository.class);
    AuditFindingRepository findings = mock(AuditFindingRepository.class);
    za.co.handyflow.platform.audittrail.application.AuditTrailFacade trail = mock(za.co.handyflow.platform.audittrail.application.AuditTrailFacade.class);
    AuditQueryService service;
    UUID tenant = UUID.randomUUID(), engA = UUID.randomUUID(), engB = UUID.randomUUID(), staffId = UUID.randomUUID(), auditorId = UUID.randomUUID();
    TenantId tid;
    Actor staff, auditor;

    @BeforeEach
    void setUp() {
        service = new AuditQueryService(repo, findings, trail);
        tid = TenantId.of(tenant);
        staff = Actor.staff(staffId, "Thandi");
        auditor = Actor.auditor(auditorId, "a@firm.test");
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private AuditQuery stored(UUID engagement) {
        var q = AuditQuery.raise(tenant, engagement, "QUERY", "Q-0001", "Round-sum journal", "Why was this journal posted for a round sum?", null, null, null, null, auditorId, "a@firm.test");
        when(repo.findByTenantIdAndId(tenant, q.getId())).thenReturn(Optional.of(q));
        return q;
    }

    @Test
    void referencesUseTheirOwnSeriesPerKind() {
        when(repo.highestNumber(tenant, "QUERY", 3)).thenReturn(6);
        when(repo.highestNumber(tenant, "REVIEW_NOTE", 4)).thenReturn(1);
        var q = service.raise(tid, Scope.engagement(engA), auditor, new RaiseRequest("QUERY", "T", "A proper question here", null, null, null, null));
        var n = service.raise(tid, Scope.engagement(engA), auditor, new RaiseRequest("REVIEW_NOTE", "T", "A proper observation here", null, null, null, null));
        assertEquals("Q-0007", q.reference());
        assertEquals("RN-0002", n.reference());
        assertEquals(engA, q.engagementId());
    }

    @Test
    void theBusinessCannotRaiseAndTheAuditorCannotAnswer() {
        assertEquals("AUDITOR_ONLY", assertThrows(HandyFlowException.class, () -> service.raise(tid, Scope.all(), staff, new RaiseRequest("QUERY", "T", "A proper question here", null, null, null, null))).getErrorCode());
        var q = stored(engA);
        assertEquals("BUSINESS_ONLY", assertThrows(HandyFlowException.class, () -> service.answer(tid, Scope.engagement(engA), auditor, q.getId(), "An answer with enough detail")).getErrorCode());
        assertEquals("AUDITOR_ONLY", assertThrows(HandyFlowException.class, () -> service.resolve(tid, Scope.all(), staff, q.getId(), null)).getErrorCode());
    }

    @Test
    void anAuditorCannotReachAnotherEngagementsQuery() {
        var q = stored(engB);
        assertThrows(ResourceNotFoundException.class, () -> service.detail(tid, Scope.engagement(engA), auditor, q.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.withdraw(tid, Scope.engagement(engA), auditor, q.getId(), "Not relevant any more"));
        when(repo.findByTenantIdOrderByCreatedAtDesc(tenant)).thenReturn(List.of(q));
        assertTrue(service.list(tid, Scope.engagement(engA), auditor).isEmpty());
        assertEquals(1, service.list(tid, Scope.all(), staff).size());
    }

    @Test
    void theBusinessSeesTheLinkedFindingButTheAuditorDoesNot() {
        var q = stored(engA);
        UUID fid = UUID.randomUUID();
        var f = mock(AuditFinding.class);
        when(f.getTitle()).thenReturn("Weak journal approval");
        when(findings.findByTenantAndId(tenant, fid)).thenReturn(Optional.of(f));
        var forBusiness = service.link(tid, staff, q.getId(), fid);
        assertEquals(fid, forBusiness.linkedFindingId());
        assertEquals("Weak journal approval", forBusiness.linkedFindingTitle());
        var forAuditor = service.detail(tid, Scope.engagement(engA), auditor, q.getId());
        assertNull(forAuditor.linkedFindingId());
        assertNull(forAuditor.linkedFindingTitle());
    }

    @Test
    void cannotLinkToAFindingThatIsNotThisBusinesss() {
        var q = stored(engA);
        UUID fid = UUID.randomUUID();
        when(findings.findByTenantAndId(tenant, fid)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.link(tid, staff, q.getId(), fid));
        assertNull(q.getLinkedFindingId());
    }

    @Test
    void onlyTheBusinessCanLink() {
        var q = stored(engA);
        assertEquals("BUSINESS_ONLY", assertThrows(HandyFlowException.class, () -> service.link(tid, auditor, q.getId(), UUID.randomUUID())).getErrorCode());
    }

    @Test
    void ruleBreachesBecomeConflictsAndAuditorStepsAreTrailedInTheirName() {
        var q = stored(engA);
        assertEquals("AUDIT_QUERY_STATE", assertThrows(HandyFlowException.class, () -> service.resolve(tid, Scope.engagement(engA), auditor, q.getId(), null)).getErrorCode());
        service.answer(tid, Scope.all(), staff, q.getId(), "It is the agreed monthly retainer accrual");
        service.resolve(tid, Scope.engagement(engA), auditor, q.getId(), "Thanks");
        verify(trail).recordAs(eq(tid), eq("AUDITOR"), eq(auditorId), eq("a@firm.test"), eq("AUDIT_QUERY_RESOLVED"), any(), eq(q.getId()), any(), any());
        verify(trail).record(eq(tid), eq("AUDIT_QUERY_ANSWERED"), any(), eq(q.getId()), any(), any());
    }
}
