package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.HrSavedReport;
import za.co.handyflow.platform.hr.domain.repository.HrSavedReportRepository;
import za.co.handyflow.platform.hr.dto.SavedReportDtos.SaveRequest;
import za.co.handyflow.platform.hr.domain.model.HrSavedReportShare;
import za.co.handyflow.platform.hr.domain.repository.HrSavedReportShareRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedReportServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14); // a Wednesday

    @Mock HrSavedReportRepository repo;
    @Mock NotificationService notifications;
    @Mock HrSavedReportShareRepository shares;
    @Mock ReportAudience audience;
    @Mock UserRecipientResolver recipients;

    SavedReportService service;
    final TenantId tenant = TenantId.generate();
    final UUID me = UUID.randomUUID();

    @BeforeEach
    void setUp() { service = new SavedReportService(repo, notifications, shares, audience, recipients); }

    @Test
    void savingAPayrollReportNeedsThePayrollRight() {
        assertThrows(AccessDeniedException.class,
                () -> service.save(tenant, me, Set.of("HR_REPORT_READ"), new SaveRequest("Cost", "PAYROLL", "LAST_MONTH", "NONE", null), TODAY));
        verify(repo, never()).save(any());
    }

    @Test
    void aPayrollReportIsSavedForSomeoneWhoSeesPay() {
        when(repo.countForOwner(tenant, me)).thenReturn(0L);
        when(repo.save(any(HrSavedReport.class))).thenAnswer(i -> i.getArgument(0));
        var v = service.save(tenant, me, Set.of("HR_REPORT_READ", "HR_COMPENSATION_READ"), new SaveRequest(" Cost ", "PAYROLL", "LAST_MONTH", "MONTHLY", 3), TODAY);
        assertEquals("Cost", v.name());
        assertEquals(LocalDate.of(2026, 9, 1), v.from());
        assertEquals(LocalDate.of(2026, 9, 30), v.to());
    }

    @Test
    void aBadSettingIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> service.save(tenant, me, Set.of("HR_REPORT_READ"), new SaveRequest("", "WORKFORCE", "LAST_MONTH", "NONE", null), TODAY));
    }

    @Test
    void thereIsALimitPerPerson() {
        when(repo.countForOwner(tenant, me)).thenReturn(20L);
        assertThrows(IllegalStateException.class,
                () -> service.save(tenant, me, Set.of("HR_REPORT_READ"), new SaveRequest("A", "WORKFORCE", "LAST_MONTH", "NONE", null), TODAY));
    }

    @Test
    void someoneElsesSavedReportCannotBeDeleted() {
        UUID id = UUID.randomUUID();
        when(repo.findForOwner(tenant, me, id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(tenant, me, id));
        verify(repo, never()).delete(any());
    }

    @Test
    void aDueReportIsRemindedOnceAndRemembered() {
        HrSavedReport r = HrSavedReport.of(tenant.getValue(), me, "Weekly", "WORKFORCE", "THIS_MONTH", "WEEKLY", 3);
        when(repo.scheduled()).thenReturn(List.of(r));
        when(shares.forReport(r.getId())).thenReturn(List.of());
        when(audience.held(tenant, me)).thenReturn(Set.of("HR_REPORT_READ"));
        when(recipients.resolveUser(tenant, me)).thenReturn(Optional.empty());
        assertEquals(1, service.remindAll(TODAY));
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(sent.capture());
        assertEquals(me, sent.getValue().recipients().get(0).userId());
        assertEquals(TODAY, r.getLastNotifiedOn());
        assertEquals(0, service.remindAll(TODAY));
        verify(notifications, times(1)).send(any(NotificationRequest.class));
    }

    @Test
    void aReportNotDueTodayIsLeftAlone() {
        HrSavedReport r = HrSavedReport.of(tenant.getValue(), me, "Weekly", "WORKFORCE", "THIS_MONTH", "WEEKLY", 2);
        when(repo.scheduled()).thenReturn(List.of(r));
        assertEquals(0, service.remindAll(TODAY));
        verifyNoInteractions(notifications);
    }

    @Test
    void aFailedSendIsNotRememberedSoItIsTriedAgain() {
        HrSavedReport r = HrSavedReport.of(tenant.getValue(), me, "Weekly", "WORKFORCE", "THIS_MONTH", "WEEKLY", 3);
        when(repo.scheduled()).thenReturn(List.of(r));
        when(shares.forReport(r.getId())).thenReturn(List.of());
        when(audience.held(tenant, me)).thenReturn(Set.of("HR_REPORT_READ"));
        when(recipients.resolveUser(tenant, me)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("down")).when(notifications).send(any(NotificationRequest.class));
        assertEquals(0, service.remindAll(TODAY));
        assertNull(r.getLastNotifiedOn());
    }

    private HrSavedReport weekly(String kind, boolean email) {
        return HrSavedReport.of(tenant.getValue(), me, "Weekly", kind, "THIS_MONTH", "WEEKLY", 3, email);
    }

    @Test
    void aSharedReportRemindsTheColleagueToo() {
        UUID ben = UUID.randomUUID();
        HrSavedReport r = weekly("WORKFORCE", false);
        when(repo.scheduled()).thenReturn(List.of(r));
        when(shares.forReport(r.getId())).thenReturn(List.of(HrSavedReportShare.of(tenant.getValue(), r.getId(), ben, me)));
        when(audience.held(tenant, me)).thenReturn(Set.of("HR_REPORT_READ"));
        when(audience.held(tenant, ben)).thenReturn(Set.of("HR_REPORT_READ"));
        when(recipients.resolveUser(any(), any())).thenReturn(Optional.empty());
        assertEquals(1, service.remindAll(TODAY));
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, times(1)).send(sent.capture());
        assertEquals(2, sent.getValue().recipients().size());
    }

    @Test
    void someoneWhoLostTheRightIsNotReminded() {
        UUID ben = UUID.randomUUID();
        HrSavedReport r = weekly("WORKFORCE", false);
        when(repo.scheduled()).thenReturn(List.of(r));
        when(shares.forReport(r.getId())).thenReturn(List.of(HrSavedReportShare.of(tenant.getValue(), r.getId(), ben, me)));
        when(audience.held(tenant, me)).thenReturn(Set.of("HR_REPORT_READ"));
        when(audience.held(tenant, ben)).thenReturn(Set.of());
        when(recipients.resolveUser(any(), any())).thenReturn(Optional.empty());
        service.remindAll(TODAY);
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(sent.capture());
        assertEquals(1, sent.getValue().recipients().size());
    }

    @Test
    void aPayrollReminderSkipsSomeoneWhoMayNotSeePay() {
        UUID ben = UUID.randomUUID();
        HrSavedReport r = weekly("PAYROLL", false);
        when(repo.scheduled()).thenReturn(List.of(r));
        when(shares.forReport(r.getId())).thenReturn(List.of(HrSavedReportShare.of(tenant.getValue(), r.getId(), ben, me)));
        when(audience.held(tenant, me)).thenReturn(Set.of("HR_REPORT_READ", "HR_COMPENSATION_READ"));
        when(audience.held(tenant, ben)).thenReturn(Set.of("HR_REPORT_READ"));
        when(recipients.resolveUser(any(), any())).thenReturn(Optional.empty());
        service.remindAll(TODAY);
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(sent.capture());
        assertEquals(List.of(me), sent.getValue().recipients().stream().map(Recipient::userId).toList());
    }

    @Test
    void theEmailGoesSeparatelyWithAWorkingLinkAndOnlyToPeopleWithAnAddress() {
        UUID ben = UUID.randomUUID();
        HrSavedReport r = weekly("WORKFORCE", true);
        when(repo.scheduled()).thenReturn(List.of(r));
        when(shares.forReport(r.getId())).thenReturn(List.of(HrSavedReportShare.of(tenant.getValue(), r.getId(), ben, me)));
        when(audience.held(any(), any())).thenReturn(Set.of("HR_REPORT_READ"));
        when(recipients.resolveUser(tenant, me)).thenReturn(Optional.of(Recipient.user(me, "Ann", "ann@acme.test", null)));
        when(recipients.resolveUser(tenant, ben)).thenReturn(Optional.of(Recipient.user(ben, "Ben", null, null)));
        service.remindAll(TODAY);
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, times(2)).send(sent.capture());
        NotificationRequest mail = sent.getAllValues().get(1);
        assertEquals(java.util.Set.of(NotificationChannel.EMAIL), mail.channels());
        assertTrue(mail.actionUrl().startsWith("http"));
        assertEquals(1, mail.recipients().size());
    }

    @Test
    void noEmailUnlessAskedFor() {
        HrSavedReport r = weekly("WORKFORCE", false);
        when(repo.scheduled()).thenReturn(List.of(r));
        when(shares.forReport(r.getId())).thenReturn(List.of());
        when(audience.held(any(), any())).thenReturn(Set.of("HR_REPORT_READ"));
        when(recipients.resolveUser(any(), any())).thenReturn(Optional.of(Recipient.user(me, "Ann", "ann@acme.test", null)));
        service.remindAll(TODAY);
        verify(notifications, times(1)).send(any(NotificationRequest.class));
    }
}
