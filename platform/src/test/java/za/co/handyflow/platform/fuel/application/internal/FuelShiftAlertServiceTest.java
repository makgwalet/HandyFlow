package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelShift;
import za.co.handyflow.platform.fuel.domain.model.FuelShiftVariancePolicy;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Written, not compiled or run. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelShiftAlertServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }
    @Mock FuelAlertRecipientService recipients;
    @Mock NotificationService notifications;
    @Mock FuelToleranceService tolerances;
    @Mock FuelShiftRepository shifts;
    @InjectMocks FuelShiftAlertService service;

    final Recipient supervisor = Recipient.user(UUID.randomUUID(), "Sup", "s@x.co", null);

    /** A closed shift with the given takings against R6,000 expected and a R5 tolerance. */
    private FuelShift closed(String cash) {
        FuelShift s = FuelShift.open(T, "SH-1", "Thabo", null, null);
        s.close(bd(cash), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, bd("0"), bd("6000"), BigDecimal.ZERO, bd("300"), "Short on the till", null, bd("5.00"), false);
        return s;
    }

    private List<NotificationRequest> sent() {
        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, atLeast(0)).send(c.capture());
        return c.getAllValues();
    }

    @Test @DisplayName("an automatic acceptance tells the people chosen for it, with the variance and the tolerance")
    void autoAcceptedNotice() {
        when(recipients.recipientsFor(T, "SHIFT_ACCEPTED")).thenReturn(List.of(supervisor));
        service.autoAccepted(T, closed("6000"));
        var n = sent().get(0);
        assertEquals(NotificationType.FUEL_SHIFT_AUTO_ACCEPTED, n.type());
        assertTrue(n.message().contains("tolerance of R 5.00"));
    }

    @Test @DisplayName("nobody chosen for the routine notice means nothing is sent")
    void nobodyNoNotice() {
        when(recipients.recipientsFor(T, "SHIFT_ACCEPTED")).thenReturn(List.of());
        service.autoAccepted(T, closed("6000"));
        verify(notifications, never()).send(any());
    }

    @Test @DisplayName("an import left for a person tells the exception recipients why")
    void notAcceptedNotice() {
        when(recipients.recipientsFor(T, "SHIFT_EXCEPTION")).thenReturn(List.of(supervisor));
        service.notAccepted(T, "EXT-9", "The takings are R 1000.00 short");
        assertTrue(sent().get(0).message().contains("EXT-9"));
        assertTrue(sent().get(0).message().contains("short"));
    }

    @Test @DisplayName("a shift within tolerance sends no exception")
    void withinToleranceQuiet() {
        when(recipients.recipientsFor(any(), any())).thenReturn(List.of(supervisor));
        service.afterClose(T, closed("5998"));
        verify(notifications, never()).send(any());
    }

    @Test @DisplayName("a shift outside tolerance tells the exception recipients")
    void outsideToleranceNotice() {
        when(recipients.recipientsFor(T, "SHIFT_EXCEPTION")).thenReturn(List.of(supervisor));
        service.afterClose(T, closed("5000"));
        var n = sent().get(0);
        assertEquals(NotificationType.FUEL_SHIFT_NEEDS_REVIEW, n.type());
        assertTrue(n.message().contains("short"));
    }

    @Test @DisplayName("repeats: alert when the count reaches the company's number, then again at each further multiple, not in between")
    void repeatAlerts() {
        when(recipients.recipientsFor(any(), any())).thenReturn(List.of(supervisor));
        when(tolerances.policy(T)).thenReturn(Optional.of(FuelShiftVariancePolicy.of(T, 3, 7, null)));
        FuelShift s = closed("5000");
        for (long n : new long[]{2, 3, 4, 5, 6}) {
            clearInvocations(notifications);
            when(shifts.countOutsideToleranceSince(eq(T), eq("Thabo"), any(Instant.class))).thenReturn(n);
            service.afterClose(T, s);
            boolean repeat = sent().stream().anyMatch(r -> r.type() == NotificationType.FUEL_SHIFT_VARIANCE_REPEATED);
            assertEquals(n == 3 || n == 6, repeat, "count " + n);
        }
    }

    @Test @DisplayName("with no repeat rule set there is no repeat alert, and a failing notice never throws")
    void noRuleAndFailuresAreSwallowed() {
        when(recipients.recipientsFor(any(), any())).thenReturn(List.of(supervisor));
        when(tolerances.policy(T)).thenReturn(Optional.empty());
        service.afterClose(T, closed("5000"));
        assertTrue(sent().stream().noneMatch(r -> r.type() == NotificationType.FUEL_SHIFT_VARIANCE_REPEATED));
        doThrow(new RuntimeException("mail down")).when(notifications).send(any());
        assertDoesNotThrow(() -> service.afterClose(T, closed("5000")));
        assertDoesNotThrow(() -> service.autoAccepted(T, closed("6000")));
    }
}
