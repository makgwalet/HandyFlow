package za.co.handyflow.platform.auditor.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Actor;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Scope;
import za.co.handyflow.platform.auditor.domain.model.EvidenceRequest;
import za.co.handyflow.platform.auditor.domain.repository.EvidenceRequestCommentRepository;
import za.co.handyflow.platform.auditor.domain.repository.EvidenceRequestRepository;
import za.co.handyflow.platform.auditor.domain.repository.ExternalAuditEngagementRepository;
import za.co.handyflow.platform.auditor.dto.EvidenceRequestDtos.CreateRequest;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EvidenceRequestServiceTest {
    EvidenceRequestRepository repo = mock(EvidenceRequestRepository.class);
    EvidenceRequestCommentRepository comments = mock(EvidenceRequestCommentRepository.class);
    ExternalAuditEngagementRepository engagements = mock(ExternalAuditEngagementRepository.class);
    EvidenceFacade evidence = mock(EvidenceFacade.class);
    UserRepository users = mock(UserRepository.class);
    za.co.handyflow.platform.audittrail.application.AuditTrailFacade trail = mock(za.co.handyflow.platform.audittrail.application.AuditTrailFacade.class);
    EvidenceRequestService service;

    UUID tenant = UUID.randomUUID(), engA = UUID.randomUUID(), engB = UUID.randomUUID(), staffId = UUID.randomUUID(), auditorId = UUID.randomUUID();
    TenantId tid;
    Actor staff, auditor;

    @BeforeEach
    void setUp() {
        service = new EvidenceRequestService(repo, comments, engagements, evidence, users, trail);
        tid = TenantId.of(tenant);
        staff = Actor.staff(staffId, "Thandi");
        auditor = Actor.auditor(auditorId, "a@firm.test");
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repo.highestNumber(tenant)).thenReturn(4);
        when(comments.findByRequestIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
    }

    private EvidenceRequest stored(UUID engagement, String byKind) {
        var r = EvidenceRequest.create(tenant, engagement, "ER-0001", "Bank statements", null, "MEDIUM", null, null, byKind, byKind.equals("STAFF") ? staffId : auditorId, "x");
        when(repo.findByTenantIdAndId(tenant, r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    private EvidenceResponse file(UUID id) { return new EvidenceResponse(id, "f.pdf", "application/pdf", 10L, "OTHER", "ACTIVE", "T", Instant.now()); }

    @Test
    void referencesRunOnFromTheHighestExisting() {
        var v = service.create(tid, Scope.all(), staff, new CreateRequest("Bank statements", null, "HIGH", null, null, null));
        assertEquals("ER-0005", v.reference());
    }

    @Test
    void anAuditorsRequestBelongsToTheirOwnEngagementWhateverTheyAsk() {
        var v = service.create(tid, Scope.engagement(engA), auditor, new CreateRequest("Debtors listing", null, null, null, UUID.randomUUID(), engB));
        assertEquals(engA, v.engagementId());
        assertNull(v.assignedTo());
    }

    @Test
    void anAuditorCannotSeeAnotherEngagementsRequest() {
        var r = stored(engB, "STAFF");
        assertThrows(ResourceNotFoundException.class, () -> service.detail(tid, Scope.engagement(engA), r.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.reject(tid, Scope.engagement(engA), auditor, r.getId(), "Not what we asked for"));
    }

    @Test
    void listIsLimitedToTheAuditorsEngagement() {
        var mine = stored(engA, "STAFF");
        var theirs = stored(engB, "STAFF");
        when(repo.findByTenantIdOrderByCreatedAtDesc(tenant)).thenReturn(List.of(mine, theirs));
        assertEquals(1, service.list(tid, Scope.engagement(engA)).size());
        assertEquals(2, service.list(tid, Scope.all()).size());
    }

    @Test
    void anAuditorCannotAnswerAndTheBusinessCannotApprove() {
        var r = stored(engA, "AUDITOR");
        assertEquals("BUSINESS_ONLY", assertThrows(HandyFlowException.class, () -> service.notAvailable(tid, Scope.engagement(engA), auditor, r.getId(), "We do not have this")).getErrorCode());
        assertEquals("BUSINESS_ONLY", assertThrows(HandyFlowException.class, () -> service.submit(tid, Scope.engagement(engA), auditor, r.getId(), "x")).getErrorCode());
        assertEquals("AUDITOR_ONLY", assertThrows(HandyFlowException.class, () -> service.approve(tid, Scope.all(), staff, r.getId(), null)).getErrorCode());
    }

    @Test
    void submitSendsOnlyFilesNotSentBefore() {
        var r = stored(engA, "AUDITOR");
        UUID old = UUID.randomUUID(), fresh = UUID.randomUUID();
        when(evidence.listFor(tid, "auditor", "AUDIT_EVIDENCE_REQUEST", r.getId())).thenReturn(List.of(file(old)));
        service.submit(tid, Scope.all(), staff, r.getId(), "first");
        service.reject(tid, Scope.engagement(engA), auditor, r.getId(), "Wrong period, please resend");
        when(evidence.listFor(tid, "auditor", "AUDIT_EVIDENCE_REQUEST", r.getId())).thenReturn(List.of(file(old), file(fresh)));
        service.submit(tid, Scope.all(), staff, r.getId(), "second");
        assertEquals(Set.of(old), r.getRounds().get(0).getEvidenceIds());
        assertEquals(Set.of(fresh), r.getRounds().get(1).getEvidenceIds());
    }

    @Test
    void submitWithNothingNewIsRefused() {
        var r = stored(engA, "AUDITOR");
        when(evidence.listFor(any(), any(), any(), any())).thenReturn(List.of());
        assertEquals("EVIDENCE_REQUEST_STATE", assertThrows(HandyFlowException.class, () -> service.submit(tid, Scope.all(), staff, r.getId(), "x")).getErrorCode());
    }

    @Test
    void onlyFilesSentInARoundCanBeDownloadedThroughTheRequest() {
        var r = stored(engA, "AUDITOR");
        UUID sent = UUID.randomUUID(), other = UUID.randomUUID();
        when(evidence.listFor(any(), any(), any(), any())).thenReturn(List.of(file(sent)));
        service.submit(tid, Scope.all(), staff, r.getId(), "x");
        service.download(tid, Scope.engagement(engA), r.getId(), sent);
        verify(evidence).download(tid, sent);
        assertThrows(ResourceNotFoundException.class, () -> service.download(tid, Scope.engagement(engA), r.getId(), other));
    }

    @Test
    void onlyTheSideThatRaisedARequestCanCancelIt() {
        var r = stored(engA, "AUDITOR");
        assertEquals("NOT_YOUR_REQUEST", assertThrows(HandyFlowException.class, () -> service.cancel(tid, Scope.all(), staff, r.getId(), "Not needed any more")).getErrorCode());
        service.cancel(tid, Scope.engagement(engA), auditor, r.getId(), "Not needed any more");
        assertEquals("CANCELLED", r.getStatus());
    }

    @Test
    void auditorActionsAreRecordedInTheirNameNotTheStaffMembers() {
        var r = stored(engA, "STAFF");
        when(evidence.listFor(any(), any(), any(), any())).thenReturn(List.of(file(UUID.randomUUID())));
        service.submit(tid, Scope.all(), staff, r.getId(), "x");
        service.approve(tid, Scope.engagement(engA), auditor, r.getId(), null);
        verify(trail).recordAs(eq(tid), eq("AUDITOR"), eq(auditorId), eq("a@firm.test"), eq("EVIDENCE_REQUEST_APPROVED"), any(), eq(r.getId()), any(), any());
    }

    @Test
    void domainRuleBreachesBecomeConflictsNotServerErrors() {
        var r = stored(engA, "STAFF");
        var e = assertThrows(HandyFlowException.class, () -> service.approve(tid, Scope.all(), auditor, r.getId(), null));
        assertEquals("EVIDENCE_REQUEST_STATE", e.getErrorCode());
    }
}
