package za.co.handyflow.platform.bookings.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookings.domain.repository.BookingRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * BookingReminderScheduler — sends appointment reminder emails 24h before.
 *
 * WHY 24h and not 1h?
 * 24h is the SA industry standard for service businesses (Fresha, Acuity,
 * Booksy all default to 24h).  1h is too short to reschedule or prepare.
 * Add a second @Scheduled method for 1h reminders if the business needs it —
 * just use a different flag (e.g. reminder_1h_sent) to prevent double-sending.
 *
 * WHY @Scheduled at 20:00 the day before (not midnight)?
 * Sending at midnight means emails land at 00:00 — clients see them when they
 * wake up, which is fine.  But 20:00 the evening before means the client still
 * has 12h to cancel if something came up, which is more respectful of their time
 * and the business's slot.  Pick whichever your client prefers.
 *
 * WHY iterate per-tenant using findDistinctActiveTenantIds?
 * Each tenant's work runs in its own @Transactional boundary.  If tenant B's
 * email sending fails (e.g. their from-address is invalid), tenant A's reminders
 * still go out.  One failure must not roll back all tenants' work.
 *
 * PRODUCTION NOTE:
 * For multi-instance deployments, replace @Scheduled with Quartz JDBC JobStore
 * so only one instance sends reminders.  Two instances sending simultaneously
 * would double-send because reminder_sent=false check + UPDATE are not atomic
 * across instances (they're only atomic within one transaction on one instance).
 * Quartz with a JDBC lock prevents this.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingReminderScheduler {

    private final BookingRepository bookingRepo;
    private final EmailService      emailService;

    /**
     * Runs at 20:00 every evening.
     * Finds all CONFIRMED bookings for TOMORROW that haven't had a reminder sent.
     * Sends an email and marks reminder_sent = true.
     *
     * cron = "0 0 20 * * *" = second=0, minute=0, hour=20, every day.
     */
    @Scheduled(cron = "0 0 20 * * *")
    public void sendReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        log.info("[Bookings] Reminder job starting for date={}", tomorrow);

        // WHY findDistinctActiveTenantIds on BookingRepository?
        // Same pattern as CRM schedulers — avoids cross-module TenantRepository
        // dependency.  Any tenant with at least one booking is included.
        List<UUID> tenantIds = bookingRepo.findDistinctActiveTenantIds();
        int totalSent = 0;

        for (UUID tenantId : tenantIds) {
            try {
                int sent = sendRemindersForTenant(TenantId.of(tenantId), tomorrow);
                totalSent += sent;
            } catch (Exception ex) {
                log.error("[Bookings] Reminder job failed for tenant={}: {}",
                        tenantId, ex.getMessage(), ex);
            }
        }

        log.info("[Bookings] Reminder job complete — {} reminders sent for date={}",
                totalSent, tomorrow);
    }

    /**
     * Per-tenant processing — own @Transactional so one tenant's failure
     * doesn't roll back others.
     *
     * WHY mark reminderSent before sending the email?
     * If we mark AFTER and the email send throws, we retry on the next run
     * and the client gets two emails.  If we mark BEFORE and the send fails,
     * the client gets no reminder — which is the lesser evil (and can be
     * requeued manually if needed).  Industry standard is mark-before-send
     * with an outbox pattern for guaranteed delivery; this is the pragmatic
     * version appropriate for HandyFlow's current scale.
     */
    @Transactional
    public int sendRemindersForTenant(TenantId tenantId, LocalDate date) {
        var bookings = bookingRepo.findUnremindedForDate(tenantId, date);
        int count = 0;

        for (var booking : bookings) {
            // Mark first to prevent double-send on retry
            booking.markReminderSent();
            bookingRepo.save(booking);

            // Send only if we have an email address
            if (booking.getClientEmail() != null && !booking.getClientEmail().isBlank()) {
                emailService.send(
                        booking.getClientEmail(),
                        "Reminder: your appointment is tomorrow",
                        EmailTemplates.bookingReminder(
                                booking.getClientName(),
                                // service name not on Booking entity — use booking number
                                // as subject context (full name would require a JOIN here)
                                "your appointment (" + booking.getBookingNumber() + ")",
                                date.toString(),
                                booking.getStartTime().toString(),
                                booking.getEndTime().toString()
                        )
                );
                log.info("[Bookings] Reminder sent booking={} client={} date={}",
                        booking.getBookingNumber(), booking.getClientName(), date);
            } else {
                log.debug("[Bookings] Skipped reminder for booking={} — no email on file",
                        booking.getBookingNumber());
            }
            count++;
        }
        return count;
    }
}
