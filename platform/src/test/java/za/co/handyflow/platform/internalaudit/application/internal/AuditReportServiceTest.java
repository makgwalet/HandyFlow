package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.application.internal.AuditReportService.Actor;
import za.co.handyflow.platform.internalaudit.domain.model.*;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuditReportServiceTest {
    AuditReportRepository reports = mock(AuditReportRepository.class);
    AuditReportSectionRepository sections = mock(AuditReportSectionRepository.class);
    AuditReportFactItemRepository items = mock(AuditReportFactItemRepository.class);
    AuditReportPersonRepository people = mock(AuditReportPersonRepository.class);
    AuditFindingRepository findings = mock(AuditFindingRepository.class);
    AuditEngagementRepository engagements = mock(AuditEngagementRepository.class);
    ApprovalFacade approvals = mock(ApprovalFacade.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    UserRepository users = mock(UserRepository.class);
    UserRecipientResolver recipients = mock(UserRecipientResolver.class);
    NotificationService notifications = mock(NotificationService.class);
    AuditReportService service;

    UUID tenant = UUID.randomUUID(), eng = UUID.randomUUID(), author = UUID.randomUUID(), reader = UUID.randomUUID(), stranger = UUID.randomUUID();
    TenantId tid;
    Actor manager, readerActor, strangerActor;

    @BeforeEach
    void setUp() {
        service = new AuditReportService(reports, sections, items, people, findings, engagements, approvals, trail, users, recipients, notifications);
        tid = TenantId.of(tenant);
        manager = new Actor(author, "Thandi", true);
        readerActor = new Actor(reader, "Chair", false);
        strangerActor = new Actor(stranger, "Someone", false);
        when(reports.save(any())).thenAnswer(i -> i.getArgument(0));
        when(engagements.findAllForTenant(tenant)).thenReturn(List.of());
        when(items.countByTenantIdAndReportIdAndStatus(eq(tenant), any(), any())).thenReturn(0L);
        when(sections.findByTenantIdAndReportIdOrderByPosition(eq(tenant), any())).thenReturn(List.of());
    }

    private AuditReport stored(String status) {
        var r = AuditReport.draft(tenant, eng, 1, "STANDARD", "Payroll audit report", author);
        if (!"DRAFT".equals(status)) { r.startFactualReview(1); r.closeFactualReview(0); }
        if ("ISSUED".equals(status)) r.issue(author, "hash", true);
        when(reports.findByTenantIdAndId(tenant, r.getId())).thenReturn(Optional.of(r));
        when(people.findByTenantIdAndReportIdOrderByAddedAt(tenant, r.getId())).thenReturn(List.of());
        return r;
    }

    private AuditReportPerson named(AuditReport r, String kind, UUID user) {
        var p = AuditReportPerson.of(tenant, r.getId(), kind, user, "Name", author);
        when(people.findByTenantIdAndReportIdOrderByAddedAt(tenant, r.getId())).thenReturn(List.of(p));
        return p;
    }

    private ApprovalRequestResponse approval(String status, Instant completed) {
        return new ApprovalRequestResponse(UUID.randomUUID(), "internalaudit", "ENGAGEMENT_REPORT", eng, status, "SEQUENTIAL", author, Instant.now(), completed, null, List.of());
    }

    @Test
    void someoneNotNamedCannotEvenSeeThatTheReportExists() {
        var r = stored("ISSUED");
        assertThrows(ResourceNotFoundException.class, () -> service.get(tid, strangerActor, r.getId()));
    }

    @Test
    void aRecipientSeesOnlyAnIssuedReportAndEachReadingIsCountedAndTrailed() {
        var draft = stored("READY");
        named(draft, "RECIPIENT", reader);
        assertThrows(ResourceNotFoundException.class, () -> service.get(tid, readerActor, draft.getId()));

        var issued = stored("ISSUED");
        var p = named(issued, "RECIPIENT", reader);
        var view = service.get(tid, readerActor, issued.getId());
        assertEquals(1, p.getViewCount());
        assertTrue(view.items().isEmpty());
        assertTrue(view.people().isEmpty());
        verify(trail).record(eq(tid), eq("AUDIT_REPORT_VIEWED"), eq("AUDIT_REPORT"), eq(issued.getId()), any(), any());
    }

    @Test
    void managersReadingDoNotCountAsRecipientViews() {
        var issued = stored("ISSUED");
        service.get(tid, manager, issued.getId());
        verify(trail, never()).record(eq(tid), eq("AUDIT_REPORT_VIEWED"), any(), any(), any(), any());
    }

    @Test
    void issuingNeedsAnApprovedSignOff() {
        var r = stored("READY");
        when(approvals.getLatestRequestForEntity(tid, "internalaudit", "ENGAGEMENT_REPORT", eng)).thenReturn(Optional.empty());
        var ex = assertThrows(HandyFlowException.class, () -> service.issue(tid, manager, r.getId()));
        assertEquals("REPORT_STATE", ex.getErrorCode());
        assertEquals("READY", r.getStatus());

        when(approvals.getLatestRequestForEntity(tid, "internalaudit", "ENGAGEMENT_REPORT", eng)).thenReturn(Optional.of(approval("REJECTED", Instant.now())));
        assertThrows(HandyFlowException.class, () -> service.issue(tid, manager, r.getId()));

        when(approvals.getLatestRequestForEntity(tid, "internalaudit", "ENGAGEMENT_REPORT", eng)).thenReturn(Optional.of(approval("APPROVED", Instant.now())));
        var v = service.issue(tid, manager, r.getId());
        assertEquals("ISSUED", r.getStatus());
        assertEquals(64, r.getContentHash().length());
    }

    @Test
    void aLaterVersionNeedsASignOffGivenAfterItWasStarted() {
        var old = AuditReport.draft(tenant, eng, 1, "STANDARD", "Report", author);
        old.startFactualReview(1); old.closeFactualReview(0); old.issue(author, "h", true);
        var v2 = AuditReport.draft(tenant, eng, 2, "STANDARD", "Report", author);
        v2.startFactualReview(1); v2.closeFactualReview(0);
        when(reports.findByTenantIdAndId(tenant, v2.getId())).thenReturn(Optional.of(v2));
        when(people.findByTenantIdAndReportIdOrderByAddedAt(tenant, v2.getId())).thenReturn(List.of());
        when(reports.findByTenantIdAndEngagementIdOrderByVersionDesc(tenant, eng)).thenReturn(List.of(v2, old));

        // the approval on record is the one for version 1, completed before version 2 existed
        when(approvals.getLatestRequestForEntity(tid, "internalaudit", "ENGAGEMENT_REPORT", eng))
                .thenReturn(Optional.of(approval("APPROVED", v2.getCreatedAt().minusSeconds(3600))));
        assertThrows(HandyFlowException.class, () -> service.issue(tid, manager, v2.getId()));

        when(approvals.getLatestRequestForEntity(tid, "internalaudit", "ENGAGEMENT_REPORT", eng))
                .thenReturn(Optional.of(approval("APPROVED", v2.getCreatedAt().plusSeconds(60))));
        service.issue(tid, manager, v2.getId());
        assertEquals("ISSUED", v2.getStatus());
        assertEquals("SUPERSEDED", old.getStatus());
    }

    @Test
    void onlyANamedReviewerOrAManagerCanRaiseAFactualComment() {
        var r = AuditReport.draft(tenant, eng, 1, "STANDARD", "Report", author);
        r.startFactualReview(1);
        when(reports.findByTenantIdAndId(tenant, r.getId())).thenReturn(Optional.of(r));
        when(people.findByTenantIdAndReportIdAndKindAndUserId(tenant, r.getId(), "REVIEWER", stranger)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.raiseItem(tid, strangerActor, r.getId(), null, "The headcount is 41, not 14"));
    }

    @Test
    void reviewCannotBeClosedWhileACommentIsOpen() {
        var r = AuditReport.draft(tenant, eng, 1, "STANDARD", "Report", author);
        r.startFactualReview(1);
        when(reports.findByTenantIdAndId(tenant, r.getId())).thenReturn(Optional.of(r));
        when(people.findByTenantIdAndReportIdOrderByAddedAt(tenant, r.getId())).thenReturn(List.of());
        when(items.countByTenantIdAndReportIdAndStatus(tenant, r.getId(), "OPEN")).thenReturn(2L);
        var ex = assertThrows(HandyFlowException.class, () -> service.closeFactualReview(tid, manager, r.getId()));
        assertEquals("REPORT_STATE", ex.getErrorCode());
        assertEquals("FACTUAL_REVIEW", r.getStatus());
    }

    @Test
    void createRefusesWhenAReportIsAlreadyInProgressOrIssued() {
        var open = AuditReport.draft(tenant, eng, 1, "STANDARD", "Report", author);
        when(engagements.findByTenantAndId(tenant, eng)).thenReturn(Optional.of(mock(AuditEngagement.class)));
        when(reports.findByTenantIdAndEngagementIdOrderByVersionDesc(tenant, eng)).thenReturn(List.of(open));
        assertEquals("REPORT_IN_PROGRESS", assertThrows(HandyFlowException.class, () -> service.create(tid, manager, eng, "STANDARD", null)).getErrorCode());
        assertEquals("REPORT_TEMPLATE", assertThrows(HandyFlowException.class, () -> service.create(tid, manager, eng, "NOPE", null)).getErrorCode());
    }
}
