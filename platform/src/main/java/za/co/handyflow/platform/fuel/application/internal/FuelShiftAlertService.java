package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.fuel.domain.model.FuelShift;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.NotificationService;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Tells the right people about shifts: a routine notice when a shift was accepted automatically, an exception when one needs review, and an
 * investigation alert when the same attendant closes shifts outside tolerance repeatedly. Who is told is the company's choice per topic
 * (see FuelAlertRecipientService). Nothing here can fail a shift close or an import: every call swallows its own errors.
 * The alerts point at the shift; they say nothing about cause or fault.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelShiftAlertService {

    private final FuelAlertRecipientService recipients;
    private final NotificationService notifications;
    private final FuelToleranceService tolerances;
    private final FuelShiftRepository shifts;
    private final FuelPushEventService pushEvents;

    public void autoAccepted(TenantId tenant, FuelShift s) {
        send(tenant, FuelAlertRecipientService.SHIFT_ACCEPTED, NotificationType.FUEL_SHIFT_AUTO_ACCEPTED,
                "Fuel: shift accepted automatically",
                "Shift " + s.getShiftNumber() + " (" + s.getAttendantName() + ") was accepted automatically. Variance R " + plain(s.getVarianceAmount())
                        + " against a tolerance of R " + plain(s.getToleranceApplied()) + ".", s.getId().toString());
    }

    /** The import was left for a person. The reason is the system's own plain-words refusal. */
    public void notAccepted(TenantId tenant, String externalId, String reason) {
        send(tenant, FuelAlertRecipientService.SHIFT_EXCEPTION, NotificationType.FUEL_SHIFT_NEEDS_REVIEW,
                "Fuel: imported shift needs review",
                "Imported shift " + externalId + " was not accepted automatically: " + (reason == null ? "it needs a person to look at it" : reason), tenant.getValue().toString());
    }

    /** After any shift closes: tell about an out-of-tolerance variance, and about repeats. */
    public void afterClose(TenantId tenant, FuelShift s) {
        try {
            if (!s.isOutsideTolerance()) return;
            pushShiftOutside(tenant, s);
            send(tenant, FuelAlertRecipientService.SHIFT_EXCEPTION, NotificationType.FUEL_SHIFT_NEEDS_REVIEW,
                    "Fuel: shift closed outside tolerance",
                    "Shift " + s.getShiftNumber() + " (" + s.getAttendantName() + ") closed R " + plain(s.getVarianceAmount().abs())
                            + (s.getVarianceAmount().signum() < 0 ? " short" : " over") + ", outside the tolerance of R " + plain(s.getToleranceApplied())
                            + ". It needs a supervisor's review.", s.getId().toString());
            tolerances.policy(tenant).ifPresent(p -> {
                long n = shifts.countOutsideToleranceSince(tenant, s.getAttendantName(), Instant.now().minus(p.getRepeatDays(), ChronoUnit.DAYS));
                if (n >= p.getRepeatCount() && (n - p.getRepeatCount()) % p.getRepeatCount() == 0) {
                    send(tenant, FuelAlertRecipientService.SHIFT_REPEAT, NotificationType.FUEL_SHIFT_VARIANCE_REPEATED,
                            "Fuel: repeated shift variances",
                            s.getAttendantName() + " has closed " + n + " shifts outside tolerance in the last " + p.getRepeatDays()
                                    + " days. This may need looking into.", s.getId().toString());
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Shift alerts failed for {}: {}", s.getShiftNumber(), ex.getMessage());
        }
    }

    /** The same people as the shift-review notice (topic SHIFT_EXCEPTION). Each shift once. */
    private void pushShiftOutside(TenantId tenant, FuelShift s) {
        try {
            List<UUID> to = recipients.recipientsFor(tenant, FuelAlertRecipientService.SHIFT_EXCEPTION).stream().map(Recipient::userId).filter(java.util.Objects::nonNull).toList();
            pushEvents.publish(tenant, FuelPushEventService.SHIFT_OUTSIDE_TOLERANCE, "SHIFT:" + s.getId(), to, "Shift closed outside tolerance",
                    "Shift " + s.getShiftNumber() + " needs a supervisor's review.", java.util.Map.of("screen", "ShiftDetail", "shiftId", String.valueOf(s.getId())));
        } catch (RuntimeException ex) { log.warn("Shift push failed for {}: {}", s.getShiftNumber(), ex.getMessage()); }
    }

    private void send(TenantId tenant, String topic, NotificationType type, String title, String message, String entityId) {
        try {
            List<Recipient> to = recipients.recipientsFor(tenant, topic);
            if (to.isEmpty()) return;
            notifications.send(NotificationRequest.builder().tenantId(tenant).type(type).title(title).message(message)
                    .actionUrl("/fuel/forecourt").sourceModule("fuel").sourceEntityId(entityId).recipients(to).build());
        } catch (RuntimeException ex) {
            log.warn("Could not send the Fuel shift notice ({}): {}", topic, ex.getMessage());
        }
    }

    private static String plain(java.math.BigDecimal v) { return v == null ? "0.00" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
}
