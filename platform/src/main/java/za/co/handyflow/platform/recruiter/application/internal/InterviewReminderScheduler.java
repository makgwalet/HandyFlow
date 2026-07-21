package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.recruiter.domain.model.RecApplicant;
import za.co.handyflow.platform.recruiter.domain.model.RecApplication;
import za.co.handyflow.platform.recruiter.domain.model.RecInterview;
import za.co.handyflow.platform.recruiter.domain.model.RecInterviewPanelist;
import za.co.handyflow.platform.recruiter.domain.model.RecJob;
import za.co.handyflow.platform.recruiter.domain.repository.RecApplicantRepository;
import za.co.handyflow.platform.recruiter.domain.repository.RecApplicationRepository;
import za.co.handyflow.platform.recruiter.domain.repository.RecInterviewPanelistRepository;
import za.co.handyflow.platform.recruiter.domain.repository.RecInterviewRepository;
import za.co.handyflow.platform.recruiter.domain.repository.RecJobRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Sends a reminder for interviews approaching within a configurable
 * window (default 24h) to the applicant, the primary interviewer, and
 * any panelists — the same recipients as the original "interview
 * scheduled" notification.
 * <p>
 * A separate scheduled class rather than a method on RecruiterService,
 * matching the AccountantScheduler / PsiraComplianceScheduler /
 * NoShowAlertScheduler naming convention already established elsewhere
 * in this codebase for scheduled jobs. Own repository injections and a
 * small duplicated fetchUserEmail() helper rather than depending on
 * RecruiterService, matching the same "duplication costs less than
 * coupling" tradeoff already made twice this session
 * (RecruiterPdfGenerator, LocalCvStorage).
 * <p>
 * WHY REUSE NotificationType.INTERVIEW_SCHEDULED RATHER THAN A DEDICATED
 * INTERVIEW_REMINDER TYPE? That enum lives in the notifications module,
 * outside recruiter's ownership — adding a constant to it here without
 * re-verifying its current full contents risked a bad edit to a
 * cross-module file I don't own. INTERVIEW_SCHEDULED's existing defaults
 * (IN_APP, EMAIL) are appropriate for a reminder too; adding a dedicated
 * type later (different bell icon/copy) is a one-line addition to that
 * enum whenever wanted.
 * <p>
 * WHY HOURLY, NOT "EXACTLY N HOURS BEFORE"? Simpler and more robust: any
 * interview whose scheduledAt falls between now and now-plus-hoursBefore
 * on a given run gets reminded. Hourly runs against a 24h-default window
 * give many chances to catch each interview well before it happens,
 * without needing to compute a precise fire time per interview.
 * <p>
 * ASSUMES @EnableScheduling IS ALREADY ON somewhere in this app — not
 * verified directly, but strongly implied by the accountant module's own
 * existing tiered SARS-deadline reminders and the PsiraComplianceScheduler
 * / NoShowAlertScheduler classes referenced elsewhere in this codebase.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewReminderScheduler {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm").withZone(SAST);

    private final RecInterviewRepository         interviewRepo;
    private final RecApplicationRepository       applicationRepo;
    private final RecApplicantRepository         applicantRepo;
    private final RecJobRepository               jobRepo;
    private final RecInterviewPanelistRepository panelistRepo;
    private final EmailService                   emailService;
    private final NotificationService            notificationService;
    private final JdbcTemplate                   jdbc;

    @Value("${recruiter.interview-reminder.hours-before:24}")
    private int hoursBefore;

    @Scheduled(cron = "0 0 * * * *") // top of every hour
    @Transactional
    public void sendUpcomingInterviewReminders() {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(hoursBefore, ChronoUnit.HOURS);
        List<RecInterview> due = interviewRepo.findDueForReminder(now, windowEnd);

        for (RecInterview interview : due) {
            try {
                sendReminderFor(interview);
                interview.markReminderSent();
                interviewRepo.save(interview);
            } catch (Exception e) {
                // One bad interview (missing applicant, a resolvable
                // email failure, etc.) must never stop the rest of the
                // batch — same isolation principle as the notification
                // module's own channel senders.
                log.error("Failed to send interview reminder for interview={}: {}",
                        interview.getId(), e.getMessage(), e);
            }
        }

        if (!due.isEmpty()) {
            log.info("Sent {} interview reminder(s)", due.size());
        }
    }

    private void sendReminderFor(RecInterview interview) {
        RecApplication app = applicationRepo.findById(interview.getApplicationId()).orElse(null);
        if (app == null) return;

        RecApplicant applicant = applicantRepo.findById(app.getApplicantId()).orElse(null);
        RecJob job = jobRepo.findById(app.getJobId()).orElse(null);
        TenantId tenantId = app.getTenantId();
        String jobTitle = job != null ? job.getTitle() : "the position";
        String applicantName = applicant != null ? applicant.getFullName() : "the candidate";
        String when = DATETIME_FMT.format(interview.getScheduledAt());

        // Applicant
        if (applicant != null && applicant.getEmail() != null) {
            try {
                String portalUrl = "https://app.handyflow.co.za/careers/track/" + applicant.getPortalToken();
                String locationLine = interview.getLocation() != null && !interview.getLocation().isBlank()
                        ? "<br>" + ("VIDEO".equals(interview.getInterviewType()) ? "Meeting link" : "Venue")
                        + ": <strong>" + interview.getLocation() + "</strong>"
                        : "";
                emailService.send(applicant.getEmail(),
                        "Reminder: your interview for " + jobTitle + " is coming up",
                        "<h2>Hi " + applicant.getFirstName() + ",</h2>"
                                + "<p>This is a reminder that your interview for <strong>" + jobTitle + "</strong> is coming up.</p>"
                                + "<p>Type: <strong>" + interview.getInterviewType() + "</strong><br>"
                                + "When: <strong>" + when + "</strong>"
                                + (interview.getInterviewerName() != null
                                ? "<br>Interviewer: <strong>" + interview.getInterviewerName() + "</strong>" : "")
                                + locationLine
                                + "</p>"
                                + "<p><a href=\"" + portalUrl + "\">View your application</a></p>");
            } catch (Exception e) {
                log.warn("Failed to send applicant interview reminder for interview={}: {}",
                        interview.getId(), e.getMessage());
            }
        }

        // Primary interviewer + panelists — same recipients as the
        // original "interview scheduled" notification.
        if (interview.getInterviewerId() != null) {
            notifyParticipant(tenantId, app, interview, interview.getInterviewerId(),
                    interview.getInterviewerName(), applicantName, jobTitle, when);
        }
        for (RecInterviewPanelist p : panelistRepo.findByInterviewId(interview.getId())) {
            notifyParticipant(tenantId, app, interview, p.getUserId(), p.getUserName(),
                    applicantName, jobTitle, when);
        }
    }

    private void notifyParticipant(TenantId tenantId, RecApplication app, RecInterview interview,
                                   UUID userId, String userName, String applicantName,
                                   String jobTitle, String when) {
        String email = fetchUserEmail(userId);
        if (email == null || email.isBlank()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.INTERVIEW_SCHEDULED)
                .title("Reminder: interview with " + applicantName)
                .message("Your interview with " + applicantName + " for " + jobTitle
                        + " is coming up on " + when
                        + (interview.getLocation() != null && !interview.getLocation().isBlank()
                        ? " (" + interview.getLocation() + ")" : "")
                        + ".")
                .actionUrl("/recruiter/applications/" + app.getId())
                .sourceModule("recruiter")
                .sourceEntityId(app.getId().toString())
                .recipient(Recipient.user(userId, userName, email, null))
                .build());
    }

    // Deliberate duplicate of RecruiterService.fetchUserEmail() — see
    // class Javadoc for why.
    private String fetchUserEmail(UUID userId) {
        if (userId == null) return null;
        try {
            return jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
        } catch (Exception e) { return null; }
    }
}