package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Daily sweep, 08:30 — next open slot after Module 4a's own
 * TrainingNotificationScheduler (08:15) in this codebase's 15-minute
 * stagger convention. Four independent sweeps, each cross-tenant then
 * grouped by TenantId — same convention as every plain-entity-
 * convention module's own scheduler:
 * <ol>
 *   <li>The provider's own accreditation expiring within 60 days —
 *       CRITICAL, since an expired accreditation directly threatens
 *       the provider's ability to issue valid certificates at all,
 *       not just one client's training record.</li>
 *   <li>Sessions starting within 3 days — WARNING (same lookahead as
 *       Module 4a's own scheduler).</li>
 *   <li>Certificates expiring within 30 days (WARNING) or already
 *       lapsed (auto-marked EXPIRED, raised CRITICAL) — same rule as
 *       Module 4a.</li>
 *   <li>Invoices past their due date and not yet PAID — WARNING,
 *       mirroring InvoicingScheduler/AccountingNotificationScheduler's
 *       own AR-aging convention, applied to this module's own
 *       receivables.</li>
 * </ol>
 * Requires 5 new {@code NotificationType} constants — see the
 * accompanying {@code TrainingProvider-NotificationType-patch-
 * instructions.md}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainProvNotificationScheduler {

    private static final int ACCREDITATION_LOOKAHEAD_DAYS = 60;
    private static final int SESSION_LOOKAHEAD_DAYS = 3;
    private static final int CERTIFICATE_EXPIRY_LOOKAHEAD_DAYS = 30;

    private final TrainProvProfileRepository profileRepository;
    private final TrainProvSessionRepository sessionRepository;
    private final TrainProvCertificateRepository certificateRepository;
    private final TrainProvInvoiceRepository invoiceRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 30 8 * * *")
    @Transactional
    public void sweep() {
        sweepAccreditationExpiry();
        sweepUpcomingSessions();
        sweepExpiringCertificates();
        sweepOverdueInvoices();
    }

    private void sweepAccreditationExpiry() {
        for (TrainProvProfile profile : profileRepository.findAllWithAccreditationAcrossTenants()) {
            if (!profile.isAccreditationExpiringWithin(ACCREDITATION_LOOKAHEAD_DAYS)) continue;
            TenantId tenantId = TenantId.of(profile.getTenantId());
            List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            if (admins.isEmpty()) continue;
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(NotificationType.TRAININGPROVIDER_ACCREDITATION_EXPIRING)
                    .recipients(admins)
                    .title("Training accreditation expiring soon")
                    .message("Your accreditation (" + profile.getAccreditationBody() + ", "
                            + profile.getAccreditationNumber() + ") expires " + profile.getAccreditationExpiry()
                            + " — renew it to keep issuing valid certificates.")
                    .actionUrl("/training-provider/profile")
                    .sourceModule("trainingprovider")
                    .sourceEntityId(profile.getId().toString())
                    .build());
        }
    }

    private void sweepUpcomingSessions() {
        LocalDate today = LocalDate.now();
        List<TrainProvSession> upcoming = sessionRepository.findUpcomingAcrossTenants(today, today.plusDays(SESSION_LOOKAHEAD_DAYS));

        upcoming.stream()
                .collect(Collectors.groupingBy(s -> TenantId.of(s.getTenantId())))
                .forEach((tenantId, sessions) -> {
                    List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
                    if (admins.isEmpty()) return;
                    for (TrainProvSession session : sessions) {
                        notificationService.send(NotificationRequest.builder()
                                .tenantId(tenantId)
                                .type(NotificationType.TRAININGPROVIDER_SESSION_UPCOMING)
                                .recipients(admins)
                                .title("Training session starting soon")
                                .message("A " + session.getSessionType().toLowerCase() + " session starting "
                                        + session.getStartDate() + " is coming up — check enrollments and logistics.")
                                .actionUrl("/training-provider/sessions/" + session.getId())
                                .sourceModule("trainingprovider")
                                .sourceEntityId(session.getId().toString())
                                .build());
                    }
                });
    }

    private void sweepExpiringCertificates() {
        List<TrainProvCertificate> validCertificates = certificateRepository.findAllValidWithExpiryAcrossTenants();

        validCertificates.stream()
                .collect(Collectors.groupingBy(c -> TenantId.of(c.getTenantId())))
                .forEach((tenantId, certificates) -> {
                    List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
                    for (TrainProvCertificate certificate : certificates) {
                        if (certificate.isExpired()) {
                            certificate.markExpired();
                            certificateRepository.save(certificate);
                            if (admins.isEmpty()) continue;
                            notificationService.send(NotificationRequest.builder()
                                    .tenantId(tenantId)
                                    .type(NotificationType.TRAININGPROVIDER_CERTIFICATE_EXPIRED)
                                    .recipients(admins)
                                    .title("Delegate certificate expired")
                                    .message(certificate.getDelegateNameSnapshot() + "'s (" + certificate.getClientNameSnapshot()
                                            + ") certificate for '" + certificate.getCourseTitleSnapshot() + "' ("
                                            + certificate.getCertificateNumber() + ") expired on " + certificate.getExpiryDate() + ".")
                                    .actionUrl("/training-provider/certificates/" + certificate.getId())
                                    .sourceModule("trainingprovider")
                                    .sourceEntityId(certificate.getId().toString())
                                    .build());
                        } else if (certificate.isExpiringWithin(CERTIFICATE_EXPIRY_LOOKAHEAD_DAYS)) {
                            if (admins.isEmpty()) continue;
                            notificationService.send(NotificationRequest.builder()
                                    .tenantId(tenantId)
                                    .type(NotificationType.TRAININGPROVIDER_CERTIFICATE_EXPIRING)
                                    .recipients(admins)
                                    .title("Delegate certificate expiring soon")
                                    .message(certificate.getDelegateNameSnapshot() + "'s (" + certificate.getClientNameSnapshot()
                                            + ") certificate for '" + certificate.getCourseTitleSnapshot() + "' ("
                                            + certificate.getCertificateNumber() + ") expires " + certificate.getExpiryDate() + ".")
                                    .actionUrl("/training-provider/certificates/" + certificate.getId())
                                    .sourceModule("trainingprovider")
                                    .sourceEntityId(certificate.getId().toString())
                                    .build());
                        }
                    }
                });
    }

    private void sweepOverdueInvoices() {
        List<TrainProvInvoice> overdue = invoiceRepository.findOverdueAcrossTenants();

        overdue.stream()
                .collect(Collectors.groupingBy(i -> TenantId.of(i.getTenantId())))
                .forEach((tenantId, invoices) -> {
                    List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
                    if (admins.isEmpty()) return;
                    for (TrainProvInvoice invoice : invoices) {
                        notificationService.send(NotificationRequest.builder()
                                .tenantId(tenantId)
                                .type(NotificationType.TRAININGPROVIDER_INVOICE_OVERDUE)
                                .recipients(admins)
                                .title("Training invoice overdue")
                                .message("Invoice " + invoice.getInvoiceNumber() + " (balance R" + invoice.balance()
                                        + ") was due " + invoice.getDueDate() + " and is still unpaid.")
                                .actionUrl("/training-provider/invoices/" + invoice.getId())
                                .sourceModule("trainingprovider")
                                .sourceEntityId(invoice.getId().toString())
                                .build());
                    }
                });
    }
}
