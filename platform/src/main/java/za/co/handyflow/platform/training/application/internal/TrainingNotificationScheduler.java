package za.co.handyflow.platform.training.application.internal;

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
import za.co.handyflow.platform.training.domain.model.TrainingCertificate;
import za.co.handyflow.platform.training.domain.model.TrainingSession;
import za.co.handyflow.platform.training.domain.repository.TrainingCertificateRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingSessionRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Daily sweep, 08:15 — next open slot after WhseNotificationScheduler's
 * 08:00 in this codebase's 15-minute stagger convention (07:00 PSIRA,
 * 07:15 Armoury, 07:30 DebtCollection, 07:45 CollAgency, 08:00
 * Warehousing, 08:15 this module).
 * <p>
 * Two independent sweeps, both cross-tenant then grouped by TenantId —
 * the same {@code Collectors.groupingBy(entity ->
 * TenantId.of(entity.getTenantId()))} convention every other
 * plain-entity-convention module's scheduler in this codebase uses:
 * <ol>
 *   <li>Sessions starting within the next 3 days — a WARNING reminder
 *       so an admin can chase outstanding enrollments/logistics.</li>
 *   <li>Certificates expiring within the next 30 days — WARNING; a
 *       certificate that has already lapsed is marked EXPIRED here
 *       (not left for a human to notice) and raised as CRITICAL,
 *       since an expired safety/compliance certificate still shown as
 *       VALID anywhere in the UI would be a real compliance risk.</li>
 * </ol>
 * Requires the same manual {@code NotificationType.java} patch pattern
 * as every other module in this engagement — see the accompanying
 * {@code Training-NotificationType-patch-instructions.md}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainingNotificationScheduler {

    private static final int SESSION_LOOKAHEAD_DAYS = 3;
    private static final int CERTIFICATE_EXPIRY_LOOKAHEAD_DAYS = 30;

    private final TrainingSessionRepository sessionRepository;
    private final TrainingCertificateRepository certificateRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 15 8 * * *")
    @Transactional
    public void sweep() {
        sweepUpcomingSessions();
        sweepExpiringCertificates();
    }

    private void sweepUpcomingSessions() {
        LocalDate today = LocalDate.now();
        List<TrainingSession> upcoming = sessionRepository.findUpcomingAcrossTenants(today, today.plusDays(SESSION_LOOKAHEAD_DAYS));

        upcoming.stream()
                .collect(Collectors.groupingBy(s -> TenantId.of(s.getTenantId())))
                .forEach((tenantId, sessions) -> {
                    List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
                    for (TrainingSession session : sessions) {
                        if (admins.isEmpty()) continue;
                        notificationService.send(NotificationRequest.builder()
                                .tenantId(tenantId)
                                .type(NotificationType.TRAINING_SESSION_UPCOMING)
                                .recipients(admins)
                                .title("Training session starting soon")
                                .message("A training session starting " + session.getStartDate() + " is coming up — check enrollments and logistics.")
                                .actionUrl("/training/sessions/" + session.getId())
                                .sourceModule("training")
                                .sourceEntityId(session.getId().toString())
                                .build());
                    }
                });
    }

    private void sweepExpiringCertificates() {
        LocalDate today = LocalDate.now();
        List<TrainingCertificate> validCertificates = certificateRepository.findAllValidWithExpiryAcrossTenants();

        validCertificates.stream()
                .collect(Collectors.groupingBy(c -> TenantId.of(c.getTenantId())))
                .forEach((tenantId, certificates) -> {
                    List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
                    for (TrainingCertificate certificate : certificates) {
                        if (certificate.isExpired()) {
                            certificate.markExpired();
                            certificateRepository.save(certificate);
                            if (admins.isEmpty()) continue;
                            notificationService.send(NotificationRequest.builder()
                                    .tenantId(tenantId)
                                    .type(NotificationType.TRAINING_CERTIFICATE_EXPIRED)
                                    .recipients(admins)
                                    .title("Training certificate expired")
                                    .message(certificate.getEmployeeNameSnapshot() + "'s certificate for '"
                                            + certificate.getCourseTitleSnapshot() + "' (" + certificate.getCertificateNumber()
                                            + ") expired on " + certificate.getExpiryDate() + " — refresher training may be required.")
                                    .actionUrl("/training/certificates/" + certificate.getId())
                                    .sourceModule("training")
                                    .sourceEntityId(certificate.getId().toString())
                                    .build());
                        } else if (certificate.isExpiringWithin(CERTIFICATE_EXPIRY_LOOKAHEAD_DAYS)) {
                            if (admins.isEmpty()) continue;
                            notificationService.send(NotificationRequest.builder()
                                    .tenantId(tenantId)
                                    .type(NotificationType.TRAINING_CERTIFICATE_EXPIRING)
                                    .recipients(admins)
                                    .title("Training certificate expiring soon")
                                    .message(certificate.getEmployeeNameSnapshot() + "'s certificate for '"
                                            + certificate.getCourseTitleSnapshot() + "' (" + certificate.getCertificateNumber()
                                            + ") expires " + certificate.getExpiryDate() + ".")
                                    .actionUrl("/training/certificates/" + certificate.getId())
                                    .sourceModule("training")
                                    .sourceEntityId(certificate.getId().toString())
                                    .build());
                        }
                    }
                });
    }
}
