package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.domain.model.AuditActionPlan;
import za.co.handyflow.platform.internalaudit.domain.model.AuditFinding;
import za.co.handyflow.platform.internalaudit.domain.model.EngagementAssignment;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditReminderSchedulerTest {
    AuditActionPlanRepository actions = mock(AuditActionPlanRepository.class);
    AuditFindingRepository findings = mock(AuditFindingRepository.class);
    EngagementAssignmentRepository assignments = mock(EngagementAssignmentRepository.class);
    AuditReportFactItemRepository factItems = mock(AuditReportFactItemRepository.class);
    AuditReportRepository reports = mock(AuditReportRepository.class);
    UserRecipientResolver resolver = mock(UserRecipientResolver.class);
    TenantAdminRecipients admins = mock(TenantAdminRecipients.class);
    NotificationService notifications = mock(NotificationService.class);
    AuditReminderScheduler scheduler;
    UUID tenant = UUID.randomUUID(), owner = UUID.randomUUID(), head = UUID.randomUUID(), findingId = UUID.randomUUID(), eng = UUID.randomUUID();
    LocalDate today = LocalDate.of(2026, 10, 15);

    @BeforeEach
    void setUp() {
        scheduler = new AuditReminderScheduler(actions, findings, assignments, factItems, reports, resolver, admins, notifications);
        when(resolver.resolveUser(any(), any())).thenAnswer(i -> Optional.of(Recipient.user(i.getArgument(1), "N", "n@x.test", null)));
        when(factItems.findOpenRaisedBefore(any())).thenReturn(List.of());
    }

    private AuditActionPlan late(int days) {
        return AuditActionPlan.create(tenant, findingId, "Fix approvals", owner, today.minusDays(days), UUID.randomUUID());
    }

    @Test
    void firstDayLateRemindsTheOwnerOnly() {
        when(actions.findOverdueAcrossTenants(today)).thenReturn(List.of(late(1)));
        assertEquals(1, scheduler.sweep(today));
        verify(notifications).send(argThat((NotificationRequest r) -> r.type() == NotificationType.AUDIT_ACTION_OVERDUE));
    }

    @Test
    void quietDaysSendNothing() {
        when(actions.findOverdueAcrossTenants(today)).thenReturn(List.of(late(3), late(10)));
        assertEquals(0, scheduler.sweep(today));
        verifyNoInteractions(notifications);
    }

    @Test
    void fourteenDaysLateIsAlsoEscalatedToTheHeadOfInternalAudit() {
        when(actions.findOverdueAcrossTenants(today)).thenReturn(List.of(late(14)));
        var finding = mock(AuditFinding.class);
        when(finding.getEngagementId()).thenReturn(eng);
        when(findings.findByTenantAndId(tenant, findingId)).thenReturn(Optional.of(finding));
        var a = mock(EngagementAssignment.class);
        when(a.getRole()).thenReturn("HEAD_OF_INTERNAL_AUDIT");
        when(a.getUserId()).thenReturn(head);
        when(assignments.findByEngagement(tenant, eng)).thenReturn(List.of(a));
        assertEquals(2, scheduler.sweep(today));
        verify(notifications).send(argThat((NotificationRequest r) -> r.type() == NotificationType.AUDIT_ACTION_ESCALATED
                && r.recipients().get(0).userId().equals(head)));
    }

    @Test
    void escalationFallsBackToCompanyAdministratorsWhenNoOneHoldsTheRole() {
        when(actions.findOverdueAcrossTenants(today)).thenReturn(List.of(late(21)));
        when(findings.findByTenantAndId(tenant, findingId)).thenReturn(Optional.empty());
        when(admins.resolveTenantAdmins(TenantId.of(tenant))).thenReturn(List.of(Recipient.user(UUID.randomUUID(), "Admin", "a@x.test", null)));
        assertEquals(2, scheduler.sweep(today));
    }

    @Test
    void aFailingNotificationDoesNotStopTheSweep() {
        when(actions.findOverdueAcrossTenants(today)).thenReturn(List.of(late(1), late(7)));
        doThrow(new RuntimeException("mail down")).doNothing().when(notifications).send(any());
        assertEquals(1, scheduler.sweep(today));
    }
}
