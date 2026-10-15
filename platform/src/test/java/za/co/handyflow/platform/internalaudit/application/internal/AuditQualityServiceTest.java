package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.identity.domain.model.User;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.application.internal.AuditQualityService.Actor;
import za.co.handyflow.platform.internalaudit.domain.model.*;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditQualityServiceTest {
    AuditQualityReviewRepository reviews = mock(AuditQualityReviewRepository.class);
    AuditQualityCheckRepository checks = mock(AuditQualityCheckRepository.class);
    AuditEngagementRepository engagements = mock(AuditEngagementRepository.class);
    EngagementAssignmentRepository assignments = mock(EngagementAssignmentRepository.class);
    AuditReportRepository reports = mock(AuditReportRepository.class);
    AuditReviewNoteRepository notes = mock(AuditReviewNoteRepository.class);
    AuditFindingRepository findings = mock(AuditFindingRepository.class);
    UserRepository users = mock(UserRepository.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    AuditQualityService service;
    UUID tenant = UUID.randomUUID(), eng = UUID.randomUUID(), lead = UUID.randomUUID(), reviewer = UUID.randomUUID();
    TenantId tid;
    Actor leadActor, reviewerActor;

    @BeforeEach
    void setUp() {
        service = new AuditQualityService(reviews, checks, engagements, assignments, reports, notes, findings, users, trail);
        tid = TenantId.of(tenant);
        leadActor = new Actor(lead, "Lead");
        reviewerActor = new Actor(reviewer, "Rev");
        when(reviews.save(any())).thenAnswer(i -> i.getArgument(0));
        when(checks.save(any())).thenAnswer(i -> i.getArgument(0));
        when(engagements.findAllForTenant(tenant)).thenReturn(List.of());
        when(engagements.findByTenantAndId(tenant, eng)).thenReturn(Optional.of(mock(AuditEngagement.class)));
        var u = mock(User.class);
        when(u.getFirstName()).thenReturn("Rev"); when(u.getLastName()).thenReturn("Iewer");
        when(users.findByIdAndTenantId(reviewer, tid)).thenReturn(Optional.of(u));
        when(reviews.findByTenantIdAndEngagementIdOrderByRequestedAtDesc(tenant, eng)).thenReturn(List.of());
        when(assignments.findByEngagement(tenant, eng)).thenReturn(List.of());
    }

    private AuditQualityReview stored() {
        var r = AuditQualityReview.request(tenant, eng, reviewer, "Rev Iewer", lead, "Lead");
        when(reviews.findByTenantIdAndId(tenant, r.getId())).thenReturn(Optional.of(r));
        when(checks.findByTenantIdAndReviewId(eq(tenant), any())).thenReturn(List.of());
        return r;
    }

    @Test
    void aMemberOfTheEngagementTeamCannotBeTheReviewer() {
        var a = mock(EngagementAssignment.class);
        when(a.getUserId()).thenReturn(reviewer);
        when(assignments.findByEngagement(tenant, eng)).thenReturn(List.of(a));
        assertEquals("QR_NOT_INDEPENDENT", assertThrows(HandyFlowException.class, () -> service.request(tid, leadActor, eng, reviewer)).getErrorCode());
    }

    @Test
    void onlyOneLiveReviewPerEngagement() {
        when(reviews.findByTenantIdAndEngagementIdOrderByRequestedAtDesc(tenant, eng))
                .thenReturn(List.of(AuditQualityReview.request(tenant, eng, reviewer, "Rev", lead, "Lead")));
        assertEquals("QR_IN_PROGRESS", assertThrows(HandyFlowException.class, () -> service.request(tid, leadActor, eng, reviewer)).getErrorCode());
    }

    @Test
    void anIndependentReviewerIsAppointedAndTheStepIsTrailed() {
        var v = service.request(tid, leadActor, eng, reviewer);
        assertEquals("REQUESTED", v.summary().status());
        assertEquals(14, v.checks().size());
        verify(trail).record(any(), org.mockito.ArgumentMatchers.eq("AUDIT_QR_REQUESTED"), any(), any(), any(), any());
    }

    @Test
    void onlyTheAppointedReviewerCanAnswer() {
        var r = stored();
        assertEquals("QR_STATE", assertThrows(HandyFlowException.class, () -> service.answer(tid, leadActor, r.getId(), "F4", "YES", null)).getErrorCode());
        service.answer(tid, reviewerActor, r.getId(), "f4", "YES", null);
        assertEquals("IN_PROGRESS", r.getStatus());
    }

    @Test
    void aNoAnswerNeedsAnExplanationAndUnknownItemsAreRejected() {
        var r = stored();
        assertEquals("QR_INVALID", assertThrows(HandyFlowException.class, () -> service.answer(tid, reviewerActor, r.getId(), "F4", "NO", null)).getErrorCode());
        assertEquals("QR_ITEM", assertThrows(HandyFlowException.class, () -> service.answer(tid, reviewerActor, r.getId(), "Z9", "YES", null)).getErrorCode());
    }

    @Test
    void cannotCompleteUntilEveryItemIsAnswered() {
        var r = stored();
        assertEquals("QR_STATE", assertThrows(HandyFlowException.class, () -> service.complete(tid, reviewerActor, r.getId(), "SATISFACTORY", "Everything was in order")).getErrorCode());
        assertEquals("REQUESTED", r.getStatus());
    }
}
