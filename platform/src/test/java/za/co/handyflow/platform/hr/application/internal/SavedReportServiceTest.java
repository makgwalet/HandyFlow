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
import za.co.handyflow.platform.notifications.application.NotificationRequest;
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

    SavedReportService service;
    final TenantId tenant = TenantId.generate();
    final UUID me = UUID.randomUUID();

    @BeforeEach
    void setUp() { service = new SavedReportService(repo, notifications); }

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
        doThrow(new RuntimeException("down")).when(notifications).send(any(NotificationRequest.class));
        assertEquals(0, service.remindAll(TODAY));
        assertNull(r.getLastNotifiedOn());
    }
}
