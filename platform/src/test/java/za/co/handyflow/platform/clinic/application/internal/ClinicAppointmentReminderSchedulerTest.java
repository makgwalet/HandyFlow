package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.clinic.domain.model.ClinicAppointment;
import za.co.handyflow.platform.clinic.domain.repository.ClinicAppointmentRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicAppointmentReminderSchedulerTest {

    private static final Instant DAYTIME = Instant.parse("2026-10-07T08:00:00Z");   // 10:00 in Johannesburg
    private static final Instant NIGHT = Instant.parse("2026-10-07T22:30:00Z");     // 00:30

    @Mock ClinicAppointmentRepository appointmentRepo;
    @Mock ClinicAppointmentReminderService reminderService;
    @InjectMocks ClinicAppointmentReminderScheduler scheduler;

    private ClinicAppointment appt(UUID id) {
        ClinicAppointment a = mock(ClinicAppointment.class);
        lenient().when(a.getId()).thenReturn(id);
        return a;
    }

    @BeforeEach
    void noop() { }

    @Test
    void doesNothingAtNight() {
        assertEquals(0, scheduler.run(NIGHT));
        verifyNoInteractions(appointmentRepo, reminderService);
    }

    @Test
    void asksForTheTwoToTwentyFourHourWindow() {
        when(appointmentRepo.findDueForReminder(any(), any())).thenReturn(List.of());
        scheduler.run(DAYTIME);
        verify(appointmentRepo).findDueForReminder(DAYTIME.plusSeconds(2 * 3600), DAYTIME.plusSeconds(24 * 3600));
    }

    @Test
    void sendsOnlyWhatItManagedToClaim() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        when(appointmentRepo.findDueForReminder(any(), any())).thenReturn(List.of(appt(a), appt(b)));
        when(reminderService.claimReminder(a)).thenReturn(true);
        when(reminderService.claimReminder(b)).thenReturn(false); // another instance got there first
        assertEquals(1, scheduler.run(DAYTIME));
        verify(reminderService).sendReminder(a);
        verify(reminderService, never()).sendReminder(b);
    }

    @Test
    void oneFailureDoesNotStopTheRest() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        when(appointmentRepo.findDueForReminder(any(), any())).thenReturn(List.of(appt(a), appt(b)));
        when(reminderService.claimReminder(any())).thenReturn(true);
        doThrow(new IllegalStateException("mail down")).when(reminderService).sendReminder(a);
        assertEquals(1, scheduler.run(DAYTIME));
        verify(reminderService).sendReminder(b);
    }
}
