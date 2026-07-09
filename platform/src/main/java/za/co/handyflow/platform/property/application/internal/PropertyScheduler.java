package za.co.handyflow.platform.property.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.property.domain.model.Lease;
import za.co.handyflow.platform.property.domain.model.LeasePayment;
import za.co.handyflow.platform.property.domain.model.Property;
import za.co.handyflow.platform.property.domain.model.Unit;
import za.co.handyflow.platform.property.domain.repository.LeasePaymentRepository;
import za.co.handyflow.platform.property.domain.repository.LeaseRepository;
import za.co.handyflow.platform.property.domain.repository.UnitRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * NEW: previously didn't exist at all. Lease.isExpiringSoon() (a single
 * 60-day boolean, used only for display badges) was the only expiry-related
 * logic anywhere in the module — nothing ever actually notified anyone.
 * This was the original module review's own #1 "fix now" priority.
 * <p>
 * Also wires up LeasePayment.markOverdue() — found, while chasing a
 * separate report of payments staying PENDING forever, to be fully
 * implemented and completely uncalled anywhere in the codebase (confirmed
 * by grep: the only match for markOverdue() was its own definition). Same
 * story for EmailTemplates.rentOverdueReminder() — built, never invoked.
 * Both are wired up together here, since marking a payment overdue without
 * telling the tenant is only half the fix.
 * <p>
 * Follows the same shape ScmNotificationService already proved out for
 * Supply Chain: a nightly @Scheduled job in SAST, try-catch around each
 * item so one bad lease/payment can't take down the whole batch, and a
 * plain tenant-contact-email lookup for the "admin/landlord" side rather
 * than anything more elaborate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyScheduler {

    private final LeaseRepository        leaseRepository;
    private final LeasePaymentRepository leasePaymentRepository;
    private final UnitRepository         unitRepository;
    private final EmailService           emailService;
    private final JdbcTemplate           jdbc;

    // Sent at 90, 60, and 30 days before a lease's end date. A lease only
    // ever gets one notice per crossing into a MORE urgent bucket than
    // whatever it was last notified at — see shouldNotify() below.
    private static final int[] THRESHOLDS_DESC = {90, 60, 30};

    @Scheduled(cron = "0 0 6 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void checkExpiringLeases() {
        LocalDate cutoff = LocalDate.now().plusDays(90);
        List<Lease> expiring = leaseRepository.findAllActiveExpiringBy(cutoff);
        log.info("[Property] Checking {} active lease(s) within 90 days of expiry", expiring.size());

        for (Lease lease : expiring) {
            try {
                processLease(lease);
            } catch (Exception e) {
                // One bad lease (missing unit, bad email, whatever) must
                // never stop the rest of the batch from being checked.
                log.error("[Property] Failed to process expiry check for lease={}: {}",
                        lease.getId(), e.getMessage(), e);
            }
        }
    }

    private void processLease(Lease lease) {
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), lease.getEndDate());
        Integer threshold = currentThreshold(daysRemaining);
        if (threshold == null) return; // more than 90 days out — findAllActiveExpiringBy already filters most of this, but guards the exact boundary

        if (!shouldNotify(lease, threshold)) return;

        Optional<Unit> unit = unitRepository.findActiveById(lease.getTenantId(), lease.getUnitId());
        if (unit.isEmpty()) {
            log.warn("[Property] Lease={} references a unit that's missing or deleted — skipping expiry notice",
                    lease.getId());
            return;
        }
        Property property = unit.get().getProperty();
        String propertyName = property != null ? property.getName() : "Unknown property";
        String unitNumber   = unit.get().getUnitNumber();
        String endDateStr   = lease.getEndDate().toString();

        boolean tenantSent = trySend(
                lease.getLesseeEmail(),
                "Your lease is ending soon — " + propertyName,
                EmailTemplates.leaseExpiringTenant(
                        lease.getLesseeName(), propertyName, unitNumber, endDateStr, (int) daysRemaining));

        boolean landlordSent = fetchTenantAdminEmail(lease.getTenantId().getValue())
                .map(adminEmail -> trySend(
                        adminEmail,
                        "Lease expiring in " + daysRemaining + " days — " + propertyName,
                        EmailTemplates.leaseExpiringLandlord(
                                lease.getLesseeName(), propertyName, unitNumber, endDateStr, (int) daysRemaining)))
                .orElse(false);

        // Recorded even if one of the two sends failed — the goal is "don't
        // spam the same threshold daily", not "guarantee both sends
        // succeeded". A failed send is logged inside trySend() either way.
        if (tenantSent || landlordSent) {
            lease.recordExpiryNotice(threshold);
            leaseRepository.save(lease);
        }
    }

    /** Only notify when crossing into a MORE urgent bucket than last notified — never the same or a less urgent one. */
    private boolean shouldNotify(Lease lease, int threshold) {
        Integer last = lease.getLastExpiryNoticeDays();
        return last == null || threshold < last;
    }

    private Integer currentThreshold(long daysRemaining) {
        for (int t : THRESHOLDS_DESC) {
            if (daysRemaining <= t) return t;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Overdue payment marking — see class-level comment for how this was found
    // ═══════════════════════════════════════════════════════════════════════

    // Offset 30 minutes from the expiry check purely so the two jobs don't
    // contend for the same connection pool slot at the exact same second —
    // no functional dependency between them.
    @Scheduled(cron = "0 30 6 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void markOverduePayments() {
        LocalDate today = LocalDate.now();
        List<LeasePayment> pastDue = leasePaymentRepository.findAllPastDueUnmarked(today);
        log.info("[Property] Marking {} past-due payment(s) as overdue", pastDue.size());

        for (LeasePayment payment : pastDue) {
            try {
                processOverduePayment(payment, today);
            } catch (Exception e) {
                log.error("[Property] Failed to process overdue payment={}: {}",
                        payment.getId(), e.getMessage(), e);
            }
        }
    }

    private void processOverduePayment(LeasePayment payment, LocalDate today) {
        payment.markOverdue();
        leasePaymentRepository.save(payment);

        leaseRepository.findActiveById(payment.getTenantId(), payment.getLeaseId()).ifPresent(lease -> {
            long daysOverdue = ChronoUnit.DAYS.between(payment.getDueDate(), today);
            String period = monthName(payment.getPeriodMonth()) + " " + payment.getPeriodYear();
            trySend(
                    lease.getLesseeEmail(),
                    "Rent payment overdue — " + period,
                    EmailTemplates.rentOverdueReminder(
                            lease.getLesseeName(),
                            payment.getBalance().toString(),
                            period,
                            String.valueOf(daysOverdue)));
        });
    }

    private String monthName(int month) {
        return java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private boolean trySend(String to, String subject, String html) {
        if (to == null || to.isBlank()) return false;
        try {
            emailService.send(to, subject, html);
            return true;
        } catch (Exception e) {
            log.error("[Property] Failed to send expiry notice to={}: {}", to, e.getMessage(), e);
            return false;
        }
    }

    // Same "tenant's own registered contact email" lookup pattern already
    // proven in ScmNotificationService.findAdminEmail() — just via
    // JdbcTemplate instead of a native EntityManager query, matching the
    // more common pattern for this kind of lookup elsewhere in this
    // codebase (Creative/Marketing/POS all use JdbcTemplate the same way).
    private Optional<String> fetchTenantAdminEmail(java.util.UUID tenantId) {
        try {
            String email = jdbc.queryForObject(
                    "SELECT email FROM tenants WHERE id = ?", String.class, tenantId);
            return Optional.ofNullable(email).filter(e -> !e.isBlank());
        } catch (Exception e) {
            log.warn("[Property] Could not look up admin email for tenant={}: {}", tenantId, e.getMessage());
            return Optional.empty();
        }
    }
}