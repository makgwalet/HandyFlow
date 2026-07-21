package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.*;
import za.co.handyflow.platform.recruiter.domain.repository.*;
import za.co.handyflow.platform.recruiter.dto.*;
import za.co.handyflow.platform.shared.*;
// CROSS-MODULE BOUNDARY NOTE: HrService lives in hr.application.internal —
// the same "internal" convention this codebase uses elsewhere to keep
// modules from reaching into each other. Importing it directly from here
// crosses that boundary. Done deliberately because there's no public HR
// facade to call instead; if one exists, or if a Modulith
// ApplicationModules verification test is in the build, swap this import.
import za.co.handyflow.platform.hr.application.internal.HrService;
import za.co.handyflow.platform.hr.dto.CreateEmployeeRequest;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
// Notification module — this one IS meant for cross-module use despite
// living partly in .internal: NotificationService's own Javadoc documents
// itself as "the single entry point every other module uses", and
// EarthAssetService/FleetService both import it directly the same way.
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecruiterService {

    private final RecJobRepository          jobRepo;
    private final RecApplicantRepository    applicantRepo;
    private final RecApplicationRepository  applicationRepo;
    private final RecInterviewRepository    interviewRepo;
    private final RecStageHistoryRepository historyRepo;
    private final EmailService              emailService;
    private final JdbcTemplate             jdbc;
    private final HrService                hrService;
    private final NotificationService      notificationService;
    private final TenantAdminRecipients    tenantAdminRecipients;
    private final RecruiterPdfGenerator    recruiterPdfGenerator;
    private final RecInterviewPanelistRepository    panelistRepo;
    private final RecJobInterviewRoundRepository    roundRepo;
    private final CvStorage                          cvStorage;

    private static final Map<String, String> STAGE_LABELS = Map.of(
            "APPLIED",    "Application Received",
            "SCREENING",  "Under Review",
            "INTERVIEW",  "Interview Stage",
            "ASSESSMENT", "Assessment Stage",
            "OFFER",      "Offer Extended",
            "HIRED",      "Hired",
            "REJECTED",   "Unsuccessful",
            "WITHDRAWN",  "Withdrawn"
    );

    // SAST, not raw Instant.toString() (which is UTC and reads like
    // "2026-07-31T07:05:00Z") — matches RecruiterPdfGenerator's formatter,
    // same fix for the same underlying issue found there first.
    private static final java.time.format.DateTimeFormatter DATETIME_FMT =
            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                    .withZone(java.time.ZoneId.of("Africa/Johannesburg"));

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RecruiterSummaryResponse getSummary(TenantId tenantId) {
        return new RecruiterSummaryResponse(
                jobRepo.countByStatus(tenantId, "OPEN"),
                jobRepo.countByStatus(tenantId, "DRAFT"),
                jobRepo.countByStatus(tenantId, "FILLED"),
                applicationRepo.countByStage(tenantId, "APPLIED"),
                applicationRepo.countByStage(tenantId, "SCREENING"),
                applicationRepo.countByStage(tenantId, "INTERVIEW"),
                applicationRepo.countByStage(tenantId, "OFFER"),
                countHiredThisMonth(tenantId)
        );
    }

    // ── Jobs ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<JobResponse> getJobs(TenantId tenantId, String status, Pageable pageable) {
        return jobRepo.findAll(tenantId, status, pageable).map(this::toJobResponse);
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(TenantId tenantId, UUID id) {
        return toJobResponse(findJob(tenantId, id));
    }

    @Transactional
    public JobResponse createJob(TenantId tenantId, UUID createdBy, CreateJobRequest req) {
        RecJob job = RecJob.create(tenantId, req.title(), req.department(),
                req.location(), req.jobType(), req.experienceLevel(),
                req.description(), req.requirements(), req.benefits(),
                req.salaryMin(), req.salaryMax(), req.showSalary(),
                req.closesAt(), createdBy);
        jobRepo.save(job);
        log.info("Created job={} title={}", job.getId(), req.title());
        return toJobResponse(job);
    }

    @Transactional
    public JobResponse updateJob(TenantId tenantId, UUID id, CreateJobRequest req) {
        RecJob job = findJob(tenantId, id);
        job.update(req.title(), req.department(), req.location(),
                req.jobType(), req.experienceLevel(), req.description(),
                req.requirements(), req.benefits(),
                req.salaryMin(), req.salaryMax(), req.showSalary(), req.closesAt());
        jobRepo.save(job);
        return toJobResponse(job);
    }

    @Transactional
    public JobResponse updateJobStatus(TenantId tenantId, UUID id, String action) {
        RecJob job = findJob(tenantId, id);
        switch (action.toUpperCase()) {
            case "PUBLISH" -> job.publish();
            case "PAUSE"   -> job.pause();
            case "CLOSE"   -> job.close();
            case "FILL"    -> job.markFilled();
            default -> throw new HandyFlowException(
                    "Unknown action: " + action, HttpStatus.BAD_REQUEST, "INVALID_ACTION");
        }
        jobRepo.save(job);
        return toJobResponse(job);
    }

    @Transactional
    public void deleteJob(TenantId tenantId, UUID id) {
        findJob(tenantId, id);
        jdbc.update("UPDATE rec_jobs SET deleted_at = NOW() WHERE id = ?", id);
    }

    // ── Interview round templates ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InterviewRoundResponse> getInterviewRounds(TenantId tenantId, UUID jobId) {
        findJob(tenantId, jobId); // 404s if the job doesn't exist or isn't this tenant's
        return roundRepo.findByJobIdOrderBySequenceAsc(jobId).stream()
                .map(this::toRoundResponse)
                .toList();
    }

    @Transactional
    public InterviewRoundResponse createInterviewRound(TenantId tenantId, UUID jobId,
                                                       InterviewRoundRequest req) {
        findJob(tenantId, jobId);
        RecJobInterviewRound round = RecJobInterviewRound.create(
                tenantId, jobId, req.name(), req.sequence(), req.description());
        roundRepo.save(round);
        return toRoundResponse(round);
    }

    @Transactional
    public InterviewRoundResponse updateInterviewRound(TenantId tenantId, UUID jobId,
                                                       UUID roundId, InterviewRoundRequest req) {
        findJob(tenantId, jobId);
        RecJobInterviewRound round = roundRepo.findByIdAndTenantId(roundId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview round", roundId.toString()));
        round.update(req.name(), req.sequence(), req.description());
        return toRoundResponse(round);
    }

    @Transactional
    public void deleteInterviewRound(TenantId tenantId, UUID jobId, UUID roundId) {
        findJob(tenantId, jobId);
        RecJobInterviewRound round = roundRepo.findByIdAndTenantId(roundId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview round", roundId.toString()));
        // Guard: refuse to delete a round that scheduled interviews already
        // reference — silently orphaning that link (round_template_id would
        // dangle, or worse, cascade if the FK were ever changed to CASCADE)
        // is worse than making the recruiter deal with existing interviews
        // first.
        long inUse = interviewRepo.countByRoundTemplateId(roundId);
        if (inUse > 0) {
            throw new HandyFlowException(
                    "This round has " + inUse + " scheduled interview(s) linked to it — cannot delete",
                    HttpStatus.BAD_REQUEST, "ROUND_IN_USE");
        }
        roundRepo.delete(round);
    }

    private InterviewRoundResponse toRoundResponse(RecJobInterviewRound r) {
        return new InterviewRoundResponse(r.getId(), r.getName(), r.getSequence(), r.getDescription());
    }

    // ── Public careers page (no auth) ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<JobResponse> getPublicJobs(TenantId tenantId) {
        String companyName = fetchTenantName(tenantId);
        return jobRepo.findOpenJobs(tenantId).stream().map(j -> {
            // Don't expose salary if show_salary = false
            if (!j.isShowSalary()) {
                return new JobResponse(j.getId(), j.getTitle(), j.getDepartment(),
                        j.getLocation(), j.getJobType(), j.getExperienceLevel(),
                        j.getDescription(), j.getRequirements(), j.getBenefits(),
                        null, null, false,
                        j.getStatus(), j.getSlug(), j.getClosesAt(),
                        j.getApplicationCount(), companyName, j.getCreatedAt());
            }
            return toJobResponse(j, companyName);
        }).toList();
    }

    @Transactional(readOnly = true)
    public JobResponse getPublicJobBySlug(TenantId tenantId, String slug) {
        RecJob job = jobRepo.findByTenantIdAndSlug(tenantId, slug)
                .orElseThrow(() -> new ResourceNotFoundException("Job", slug));
        if (!"OPEN".equals(job.getStatus())) {
            throw new HandyFlowException("This position is no longer accepting applications",
                    HttpStatus.GONE, "JOB_CLOSED");
        }
        return toJobResponse(job, fetchTenantName(tenantId));
    }

    // ── Applications — public submission (no auth) ────────────────────────────

    @Transactional
    public PublicApplicationResponse submitApplication(TenantId tenantId, UUID jobId,
                                                       SubmitApplicationRequest req) {
        RecJob job = findJob(tenantId, jobId);
        if (!"OPEN".equals(job.getStatus())) {
            throw new HandyFlowException("This position is no longer accepting applications",
                    HttpStatus.BAD_REQUEST, "JOB_CLOSED");
        }

        // Store the CV via CvStorage (local disk for now, dev phase — see
        // CvStorage's own Javadoc) rather than the raw base64 string.
        // Computed once, reused for both the create() and updateCv() paths
        // below, so a brand-new applicant doesn't get the same file
        // written to disk twice under two different UUIDs.
        String cvReference = req.cvBase64() != null ? storeCv(req.cvBase64(), req.cvFileName()) : null;

        // Upsert applicant — same person may apply to multiple jobs
        RecApplicant applicant = applicantRepo
                .findByTenantIdAndEmail(tenantId, req.email().toLowerCase())
                .orElseGet(() -> RecApplicant.create(tenantId,
                        req.firstName(), req.lastName(), req.email(),
                        req.phone(), req.location(), req.linkedinUrl(),
                        req.portfolioUrl(), cvReference, req.cvFileName()));

        // Update CV if provided
        if (cvReference != null) applicant.updateCv(cvReference, req.cvFileName());
        applicantRepo.save(applicant);

        // Check for duplicate application
        if (applicationRepo.findByJobIdAndApplicantId(jobId, applicant.getId()).isPresent()) {
            throw new HandyFlowException("You have already applied for this position",
                    HttpStatus.BAD_REQUEST, "DUPLICATE_APPLICATION");
        }

        RecApplication application = RecApplication.create(tenantId, jobId,
                applicant.getId(), req.source(), req.referrerName());
        applicationRepo.save(application);

        // Update job application count
        job.incrementApplicationCount();
        jobRepo.save(job);

        // Add stage history
        historyRepo.save(RecStageHistory.create(application.getId(),
                null, "APPLIED", applicant.getFullName(), "Application submitted"));

        // Send confirmation email to applicant
        sendApplicationConfirmation(applicant, job, application);

        // Notify recruiting staff — previously the highest-frequency event in
        // this module (a candidate applying) surfaced nowhere internally;
        // staff would only see it by manually checking the pipeline.
        notifyStaffNewApplication(tenantId, applicant, job, application);

        log.info("Application received: job={} applicant={}", job.getTitle(), applicant.getEmail());

        return toPublicResponse(application, job, applicant, fetchTenantName(tenantId));
    }

    // ── Public applicant portal (token-gated, no login) ───────────────────────

    @Transactional(readOnly = true)
    public List<PublicApplicationResponse> getMyApplications(String token) {
        RecApplicant applicant = findByToken(token);
        return applicationRepo.findByApplicantId(applicant.getId()).stream()
                .map(app -> {
                    RecJob job = jobRepo.findById(app.getJobId()).orElse(null);
                    String companyName = job != null
                            ? fetchTenantName(job.getTenantId()) : "HandyFlow";
                    return toPublicResponse(app, job, applicant, companyName);
                }).toList();
    }

    @Transactional
    public void updateApplicantProfile(String token, UpdateApplicantRequest req) {
        RecApplicant applicant = findByToken(token);
        applicant.updateProfile(req.phone(), req.location(),
                req.linkedinUrl(), req.portfolioUrl());
        if (req.cvBase64() != null) applicant.updateCv(storeCv(req.cvBase64(), req.cvFileName()), req.cvFileName());
        applicantRepo.save(applicant);
    }

    @Transactional
    public void withdrawApplication(String token, UUID applicationId) {
        RecApplicant applicant = findByToken(token);
        RecApplication app = applicationRepo.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));
        if (!app.getApplicantId().equals(applicant.getId())) {
            throw new HandyFlowException("Not authorised", HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        String prevStage = app.getStage();
        app.withdraw();
        applicationRepo.save(app);
        historyRepo.save(RecStageHistory.create(applicationId,
                prevStage, "WITHDRAWN", applicant.getFullName(), "Applicant withdrew"));
    }

    // ── Applications — staff pipeline management ──────────────────────────────

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> getApplications(TenantId tenantId, UUID jobId,
                                                     String stage, Pageable pageable) {
        Page<RecApplication> apps = jobId != null
                ? applicationRepo.findByJob(jobId, stage, pageable)
                : applicationRepo.findAll(tenantId, stage, pageable);
        return apps.map(a -> toApplicationResponse(a, false));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(TenantId tenantId, UUID id) {
        RecApplication app = applicationRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", id.toString()));
        return toApplicationResponse(app, true);
    }

    @Transactional(readOnly = true)
    public byte[] getCvBytes(TenantId tenantId, UUID applicationId) {
        RecApplication app = applicationRepo.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));
        RecApplicant applicant = applicantRepo.findById(app.getApplicantId())
                .orElseThrow(() -> new ResourceNotFoundException("Applicant", app.getApplicantId().toString()));

        if (applicant.getCvUrl() == null || applicant.getCvUrl().isBlank()) {
            throw new HandyFlowException("No CV uploaded for this candidate",
                    HttpStatus.NOT_FOUND, "NO_CV");
        }

        String raw = applicant.getCvUrl();

        // New-style: a CvStorage reference (e.g. "local://<uuid>.pdf").
        // CvStorage.retrieve() throws IllegalArgumentException for
        // anything it doesn't recognize as its own reference format —
        // used here to detect "this isn't one of mine" and fall through
        // to the legacy path, rather than every caller needing its own
        // prefix-sniffing logic.
        try {
            return cvStorage.retrieve(raw);
        } catch (IllegalArgumentException notAReference) {
            // Fall through — likely a legacy row written before CvStorage
            // existed, where cv_url holds the raw base64 PDF directly.
        }

        // Defensive strip of a data: URI prefix, even though the write path
        // (SubmitApplicationRequest.cvBase64 / UpdateApplicantRequest.cvBase64)
        // is documented as raw base64, not a data URI — cheap insurance
        // against a value that somehow got a "data:application/pdf;base64,"
        // prefix from somewhere else.
        int comma = raw.indexOf(',');
        String base64 = raw.startsWith("data:") && comma >= 0 ? raw.substring(comma + 1) : raw;

        try {
            return java.util.Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            log.error("Failed to decode stored CV for applicant={}: {}", applicant.getId(), e.getMessage());
            throw new HandyFlowException("Stored CV data could not be read",
                    HttpStatus.INTERNAL_SERVER_ERROR, "CV_CORRUPT");
        }
    }

    private String storeCv(String base64, String fileName) {
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            return cvStorage.store(bytes, fileName);
        } catch (IllegalArgumentException e) {
            log.error("Failed to decode uploaded CV base64: {}", e.getMessage());
            throw new HandyFlowException("The uploaded CV file could not be read",
                    HttpStatus.BAD_REQUEST, "CV_DECODE_FAILED");
        }
    }

    @Transactional
    public ApplicationResponse updateReferral(TenantId tenantId, UUID applicationId,
                                              LinkReferralRequest req) {
        RecApplication app = applicationRepo.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));

        app.linkReferral(req.referredByUserId(), req.bonusAmount());
        // Separate from linkReferral() deliberately — a status transition
        // (PENDING -> APPROVED -> PAID) is a distinct action from linking
        // who referred them, and req.bonusStatus() being null here means
        // "not touching status this call", not "clear it".
        if (req.bonusStatus() != null) app.updateReferralBonusStatus(req.bonusStatus());

        applicationRepo.save(app);
        return toApplicationResponse(app, true);
    }

    @Transactional
    public ApplicationResponse moveStage(TenantId tenantId, UUID id,
                                         MoveStageRequest req, String movedByName) {
        RecApplication app = applicationRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", id.toString()));

        // Guardrail: RecApplication.isActive() already existed but nothing
        // called it — HIRED/REJECTED/WITHDRAWN applications could previously
        // be moved to any other stage, rejected again, etc.
        if (!app.isActive()) {
            throw new HandyFlowException(
                    "This application is in a terminal stage (" + app.getStage() + ") and cannot be moved",
                    HttpStatus.BAD_REQUEST, "APPLICATION_TERMINAL");
        }

        String prevStage = app.getStage();

        if ("REJECTED".equals(req.stage())) {
            app.reject(req.rejectionReason());
            notifyApplicantRejected(app, req.rejectionReason(), tenantId);
        } else {
            app.moveToStage(req.stage());
            if ("OFFER".equals(req.stage())) {
                // Capture offer terms here, at the point the offer is
                // actually extended — not later at conversion time, which
                // is too late for a real offer letter (candidate hasn't
                // accepted yet at conversion).
                app.recordOfferTerms(req.offeredSalary(), req.offeredSalaryFrequency(),
                        req.offeredStartDate(), req.offerBenefits());
            }
            notifyApplicantStageChange(app, req.stage(), tenantId);
        }
        applicationRepo.save(app);

        // Record history
        historyRepo.save(RecStageHistory.create(id, prevStage,
                req.stage(), movedByName, req.notes()));

        log.info("Application={} moved {} → {}", id, prevStage, req.stage());
        return toApplicationResponse(app, false);
    }

    @Transactional
    public ApplicationResponse scoreApplication(TenantId tenantId, UUID id,
                                                ScoreApplicationRequest req) {
        RecApplication app = applicationRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", id.toString()));
        app.updateScore(req.score());
        app.updateNotes(req.notes());
        applicationRepo.save(app);
        return toApplicationResponse(app, false);
    }

    // ── Interviews ────────────────────────────────────────────────────────────

    @Transactional
    public InterviewResponse scheduleInterview(TenantId tenantId, UUID applicationId,
                                               ScheduleInterviewRequest req) {
        RecApplication app = applicationRepo.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));

        if (!app.isActive()) {
            throw new HandyFlowException(
                    "This application is in a terminal stage (" + app.getStage() + ") — cannot schedule an interview",
                    HttpStatus.BAD_REQUEST, "APPLICATION_TERMINAL");
        }

        RecInterview interview = RecInterview.create(applicationId, tenantId.getValue(),
                req.interviewType(), req.scheduledAt(),
                req.interviewerId(), req.interviewerName(), req.location(),
                req.roundTemplateId());
        interviewRepo.save(interview);

        if (req.panelists() != null) {
            for (PanelistRequest p : req.panelists()) {
                if (p.userId() == null) continue;
                panelistRepo.save(RecInterviewPanelist.create(interview.getId(), p.userId(), p.userName()));
            }
        }

        // Notify everyone involved — previously scheduleInterview() captured
        // interviewerId/interviewerName but never told anyone an interview
        // had actually been booked. Panelists get the same notification as
        // the primary interviewer, each to their own inbox/bell.
        notifyInterviewer(tenantId, app, interview);
        if (req.panelists() != null) {
            for (PanelistRequest p : req.panelists()) {
                if (p.userId() == null) continue;
                notifyInterviewParticipant(tenantId, app, interview, p.userId(), p.userName());
            }
        }
        notifyApplicantInterviewScheduled(app, interview);

        return toInterviewResponse(interview);
    }

    @Transactional
    public InterviewResponse rescheduleInterview(TenantId tenantId, UUID applicationId,
                                                 UUID interviewId, RescheduleInterviewRequest req) {
        RecApplication app = applicationRepo.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));

        if (!app.isActive()) {
            throw new HandyFlowException(
                    "This application is in a terminal stage (" + app.getStage() + ") — cannot reschedule an interview",
                    HttpStatus.BAD_REQUEST, "APPLICATION_TERMINAL");
        }

        RecInterview old = interviewRepo.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview", interviewId.toString()));
        if (!old.getApplicationId().equals(applicationId)) {
            throw new ResourceNotFoundException("Interview", interviewId.toString());
        }

        old.markRescheduled(req.reason());
        interviewRepo.save(old);

        // Merge: anything not explicitly overridden carries over from the
        // interview being replaced — a reschedule is usually "same plan,
        // different time," not a whole new interview.
        String interviewType   = req.interviewType()   != null ? req.interviewType()   : old.getInterviewType();
        UUID   interviewerId   = req.interviewerId()   != null ? req.interviewerId()   : old.getInterviewerId();
        String interviewerName = req.interviewerId()   != null ? req.interviewerName() : old.getInterviewerName();
        String location        = req.location()        != null ? req.location()        : old.getLocation();
        UUID   roundTemplateId = req.roundTemplateId()  != null ? req.roundTemplateId() : old.getRoundTemplateId();

        RecInterview replacement = RecInterview.create(applicationId, tenantId.getValue(),
                interviewType, req.scheduledAt(), interviewerId, interviewerName,
                location, roundTemplateId, old.getId());
        interviewRepo.save(replacement);

        List<PanelistRequest> panelists = req.panelists() != null
                ? req.panelists()
                : panelistRepo.findByInterviewId(old.getId()).stream()
                .map(p -> new PanelistRequest(p.getUserId(), p.getUserName()))
                .toList();
        for (PanelistRequest p : panelists) {
            if (p.userId() == null) continue;
            panelistRepo.save(RecInterviewPanelist.create(replacement.getId(), p.userId(), p.userName()));
        }

        if (interviewerId != null) {
            notifyInterviewParticipantRescheduled(tenantId, app, replacement, interviewerId, interviewerName, req.reason());
        }
        for (PanelistRequest p : panelists) {
            if (p.userId() == null) continue;
            notifyInterviewParticipantRescheduled(tenantId, app, replacement, p.userId(), p.userName(), req.reason());
        }
        notifyApplicantInterviewRescheduled(app, replacement, req.reason());

        return toInterviewResponse(replacement);
    }

    @Transactional
    public InterviewResponse recordOutcome(TenantId tenantId, UUID applicationId,
                                           UUID interviewId, RecordInterviewOutcomeRequest req) {
        RecInterview interview = interviewRepo.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview", interviewId.toString()));
        interview.recordOutcome(req.outcome(), req.notes(), req.score());
        interviewRepo.save(interview);
        return toInterviewResponse(interview);
    }

    // ── Convert to HR employee ────────────────────────────────────────────────

    @Transactional
    public UUID convertToEmployee(TenantId tenantId, UUID applicationId,
                                  ConvertToEmployeeRequest req, UUID convertedByUserId) {
        RecApplication app = applicationRepo.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));

        // Guardrail: previously this method had no stage check at all — any
        // application in any stage could be converted. Require HIRED first,
        // and refuse to convert the same application twice.
        if (!"HIRED".equals(app.getStage())) {
            throw new HandyFlowException(
                    "Only applications in the HIRED stage can be converted to an employee",
                    HttpStatus.BAD_REQUEST, "NOT_HIRED");
        }
        if (app.getHrEmployeeId() != null) {
            throw new HandyFlowException(
                    "This application has already been converted",
                    HttpStatus.BAD_REQUEST, "ALREADY_CONVERTED");
        }

        RecApplicant applicant = applicantRepo.findById(app.getApplicantId())
                .orElseThrow(() -> new ResourceNotFoundException("Applicant", app.getApplicantId().toString()));

        RecJob job = jobRepo.findById(app.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job", app.getJobId().toString()));

        String jobTitle    = req.jobTitle()    != null ? req.jobTitle()    : job.getTitle();
        String department  = req.department()  != null ? req.department()  : job.getDepartment();

        String convertedByName = fetchUserName(convertedByUserId);
        if (convertedByName == null || convertedByName.isBlank()) convertedByName = "Recruiter";

        if (!req.createHrRecord()) {
            // External / agency placement — the candidate joins a client
            // company, not this tenant, so no hr_employees row belongs here.
            // Record the decision in the audit trail so it reads as
            // deliberate, not a skipped step.
            historyRepo.save(RecStageHistory.create(applicationId, "HIRED", "HIRED",
                    convertedByName, "Marked as placed externally — no HR record created"));
            log.info("Application={} marked placed externally, no HR record", applicationId);
            return null;
        }

        if (req.grossSalary() == null) {
            throw new HandyFlowException(
                    "grossSalary is required to create an HR record",
                    HttpStatus.BAD_REQUEST, "SALARY_REQUIRED");
        }

        // Route through the real HR module instead of a raw JDBC insert.
        // This reuses the one real EmployeeNumberGenerator (previously this
        // method had its own separate, equally race-prone COUNT(*)+1 copy)
        // and seeds statutory leave balances, which the old raw-insert path
        // skipped entirely — every applicant hired through here previously
        // started with zero leave on the books.
        CreateEmployeeRequest hrReq = new CreateEmployeeRequest(
                applicant.getFirstName(), applicant.getLastName(),
                null, null, null, null, null,                 // idNumber/taxNumber/dob/gender/race — not captured at application stage, completed later in HR
                applicant.getEmail(), applicant.getPhone(),
                req.startDate(), req.employmentType(),
                jobTitle, department,
                req.grossSalary(), req.payFrequency(),
                null, null, null,                              // banking — completed later in HR
                null, null, null,                              // medical/pension/travel — completed later in HR
                null, null,                                     // emergency contact — completed later in HR
                "Converted from recruiter application " + applicationId
        );
        EmployeeResponse created = hrService.createEmployee(tenantId, hrReq);
        // UNVERIFIED: created.id() / created.employeeNumber() are inferred from
        // HrService.toEmployeeResponse()'s constructor call order
        // (new EmployeeResponse(e.getId(), e.getEmployeeNumber(), ...)), not
        // from EmployeeResponse.java itself, which wasn't provided. If the
        // record's component names differ from these, this line won't
        // compile — that's intentional, so it fails loudly at compile time
        // rather than silently. Confirm against the real file before merging.
        UUID employeeId = created.id();

        app.linkToEmployee(employeeId);
        applicationRepo.save(app);

        historyRepo.save(RecStageHistory.create(applicationId, "HIRED", "HIRED",
                convertedByName, "Converted to HR employee " + created.employeeNumber()));

        log.info("Converted applicant={} to employee={}", applicant.getEmail(), employeeId);
        return employeeId;
    }

    // ── PDFs ─────────────────────────────────────────────────────────────────
    // Generation itself lives in RecruiterPdfGenerator (own repo access, own
    // OpenPDF document building) — matching CreativePdfGenerator's relationship
    // to CreativeService. This method stays here because it's a business
    // action (sends an email, mutates offerLetterSentAt), not pure generation.

    @Transactional
    public void sendOfferLetter(TenantId tenantId, UUID applicationId) {
        RecApplication app = applicationRepo.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));
        RecApplicant applicant = applicantRepo.findById(app.getApplicantId())
                .orElseThrow(() -> new ResourceNotFoundException("Applicant", app.getApplicantId().toString()));
        if (applicant.getEmail() == null) {
            throw new HandyFlowException("Applicant has no email on file",
                    HttpStatus.BAD_REQUEST, "NO_EMAIL");
        }
        String jobTitle = jobRepo.findById(app.getJobId()).map(RecJob::getTitle).orElse("the position");
        byte[] pdf = recruiterPdfGenerator.generateOfferLetter(tenantId, applicationId);

        emailService.sendWithAttachment(applicant.getEmail(),
                "Your offer — " + jobTitle,
                "<h2>Hi " + applicant.getFirstName() + ",</h2>"
                        + "<p>Please find your offer letter for <strong>" + jobTitle + "</strong> attached.</p>"
                        + "<p>Congratulations — we look forward to hearing from you.</p>",
                "Offer Letter - " + applicant.getFullName() + ".pdf", pdf);

        app.markOfferLetterSent();
        applicationRepo.save(app);
        log.info("Offer letter sent for application={}", applicationId);
    }


    @Transactional(readOnly = true)
    public List<JobResponse> getPublicJobsBySlug(String slug) {
        return getPublicJobs(resolveTenantBySlug(slug));
    }

    @Transactional(readOnly = true)
    public JobResponse getPublicJobBySlugAndTenant(String tenantSlug, String jobSlug) {
        return getPublicJobBySlug(resolveTenantBySlug(tenantSlug), jobSlug);
    }

    @Transactional
    public PublicApplicationResponse submitApplicationBySlug(String tenantSlug,
                                                             UUID jobId,
                                                             SubmitApplicationRequest req) {
        return submitApplication(resolveTenantBySlug(tenantSlug), jobId, req);
    }

    // FIX: moveStage now accepts UUID instead of hardcoded string.
    // Overloaded — the UUID version resolves the real user name.
    @Transactional
    public ApplicationResponse moveStage(TenantId tenantId, UUID id,
                                         MoveStageRequest req, UUID movedById) {
        String name = fetchUserName(movedById);
        if (name == null || name.isBlank()) name = "Recruiter";
        return moveStage(tenantId, id, req, name);
    }

    private TenantId resolveTenantBySlug(String slug) {
        try {
            String id = jdbc.queryForObject(
                    "SELECT id::text FROM tenants WHERE slug = ?", String.class, slug);
            return TenantId.of(id);
        } catch (Exception e) {
            throw new HandyFlowException("Company not found: " + slug,
                    org.springframework.http.HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
    }

    private String fetchUserName(UUID userId) {
        if (userId == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT first_name || ' ' || last_name FROM users WHERE id = ?",
                    String.class, userId);
        } catch (Exception e) { return null; }
    }

    // ASSUMPTION, not verified against the real users table schema: a
    // column literally named "email" exists. fetchUserName() above proves
    // the table/row-lookup pattern works; only the column name here is new.
    // Confirm against the real migration before relying on this in prod.
    private String fetchUserEmail(UUID userId) {
        if (userId == null) return null;
        try {
            return jdbc.queryForObject(
                    "SELECT email FROM users WHERE id = ?",
                    String.class, userId);
        } catch (Exception e) { return null; }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private RecJob findJob(TenantId tenantId, UUID id) {
        return jobRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", id.toString()));
    }

    private RecApplicant findByToken(String token) {
        return applicantRepo.findByPortalToken(token)
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid tracking link", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private void notifyStaffNewApplication(TenantId tenantId, RecApplicant applicant,
                                           RecJob job, RecApplication application) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.NEW_APPLICATION_RECEIVED)
                .title("New application: " + job.getTitle())
                .message(applicant.getFullName() + " applied for " + job.getTitle() + ".")
                .actionUrl("/recruiter/applications/" + application.getId())
                .sourceModule("recruiter")
                .sourceEntityId(application.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyInterviewer(TenantId tenantId, RecApplication app, RecInterview interview) {
        if (interview.getInterviewerId() == null) return; // free-text name only, no platform user to notify
        notifyInterviewParticipant(tenantId, app, interview,
                interview.getInterviewerId(), interview.getInterviewerName());
    }

    // Shared by the primary interviewer and each panelist — same
    // notification, different recipient. Extracted so panel members get
    // exactly the same treatment as the primary interviewer, not a
    // second, drifted copy of this logic.
    private void notifyInterviewParticipant(TenantId tenantId, RecApplication app,
                                            RecInterview interview, UUID userId, String userName) {
        String email = fetchUserEmail(userId);
        if (email == null || email.isBlank()) {
            log.warn("Interview participant={} has no resolvable email — skipping notification", userId);
            return;
        }

        String applicantName = applicantRepo.findById(app.getApplicantId())
                .map(RecApplicant::getFullName).orElse("a candidate");
        String jobTitle = jobRepo.findById(app.getJobId())
                .map(RecJob::getTitle).orElse("a position");

        Recipient recipient = Recipient.user(userId, userName, email, null);

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.INTERVIEW_SCHEDULED)
                .title("Interview scheduled: " + applicantName)
                .message("You're scheduled to interview " + applicantName + " for " + jobTitle
                        + (interview.getScheduledAt() != null
                        ? " on " + DATETIME_FMT.format(interview.getScheduledAt())
                        : " — time to be confirmed")
                        + (interview.getLocation() != null && !interview.getLocation().isBlank()
                        ? " (" + interview.getLocation() + ")" : "")
                        + ".")
                .actionUrl("/recruiter/applications/" + app.getId())
                .sourceModule("recruiter")
                .sourceEntityId(app.getId().toString())
                .recipient(recipient)
                .build());
    }

    private void notifyApplicantInterviewScheduled(RecApplication app, RecInterview interview) {
        applicantRepo.findById(app.getApplicantId()).ifPresent(applicant -> {
            if (applicant.getEmail() == null) return;
            try {
                String jobTitle = jobRepo.findById(app.getJobId())
                        .map(RecJob::getTitle).orElse("the position");
                String portalUrl = "https://app.handyflow.co.za/careers/track/" + applicant.getPortalToken();
                String when = interview.getScheduledAt() != null
                        ? DATETIME_FMT.format(interview.getScheduledAt()) : "to be confirmed";
                boolean isVideo = "VIDEO".equals(interview.getInterviewType());
                String locationLine = interview.getLocation() != null && !interview.getLocation().isBlank()
                        ? "<br>" + (isVideo ? "Meeting link" : "Venue") + ": <strong>"
                        + interview.getLocation() + "</strong>"
                        : "";
                // Panelist names, alongside the primary interviewer — not
                // instead of. "Interviewer: A" stays as the lead; panel
                // members (if any) get their own line so the applicant
                // knows who else will be in the room.
                List<String> panelistNames = panelistRepo.findByInterviewId(interview.getId()).stream()
                        .map(RecInterviewPanelist::getUserName)
                        .filter(n -> n != null && !n.isBlank())
                        .toList();
                String panelLine = panelistNames.isEmpty() ? ""
                        : "<br>Also interviewing: <strong>" + String.join(", ", panelistNames) + "</strong>";
                emailService.send(applicant.getEmail(),
                        "Interview scheduled — " + jobTitle,
                        "<h2>Hi " + applicant.getFirstName() + ",</h2>"
                                + "<p>An interview has been scheduled for your application to <strong>" + jobTitle + "</strong>.</p>"
                                + "<p>Type: <strong>" + interview.getInterviewType() + "</strong><br>"
                                + "When: <strong>" + when + "</strong>"
                                + (interview.getInterviewerName() != null
                                ? "<br>Interviewer: <strong>" + interview.getInterviewerName() + "</strong>" : "")
                                + panelLine
                                + locationLine
                                + "</p>"
                                + "<p><a href=\"" + portalUrl + "\">View your application</a></p>");
            } catch (Exception e) {
                log.warn("Failed to send interview-scheduled email: {}", e.getMessage());
            }
        });
    }

    private void notifyInterviewParticipantRescheduled(TenantId tenantId, RecApplication app,
                                                       RecInterview interview, UUID userId,
                                                       String userName, String reason) {
        String email = fetchUserEmail(userId);
        if (email == null || email.isBlank()) {
            log.warn("Interview participant={} has no resolvable email — skipping reschedule notification", userId);
            return;
        }
        String applicantName = applicantRepo.findById(app.getApplicantId())
                .map(RecApplicant::getFullName).orElse("a candidate");
        String jobTitle = jobRepo.findById(app.getJobId())
                .map(RecJob::getTitle).orElse("a position");

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.INTERVIEW_SCHEDULED)
                .title("Interview rescheduled: " + applicantName)
                .message("Your interview with " + applicantName + " for " + jobTitle + " has been rescheduled"
                        + (interview.getScheduledAt() != null
                        ? " to " + DATETIME_FMT.format(interview.getScheduledAt())
                        : " — new time to be confirmed")
                        + (interview.getLocation() != null && !interview.getLocation().isBlank()
                        ? " (" + interview.getLocation() + ")" : "")
                        + ". Reason: " + reason + ".")
                .actionUrl("/recruiter/applications/" + app.getId())
                .sourceModule("recruiter")
                .sourceEntityId(app.getId().toString())
                .recipient(Recipient.user(userId, userName, email, null))
                .build());
    }

    private void notifyApplicantInterviewRescheduled(RecApplication app, RecInterview interview, String reason) {
        applicantRepo.findById(app.getApplicantId()).ifPresent(applicant -> {
            if (applicant.getEmail() == null) return;
            try {
                String jobTitle = jobRepo.findById(app.getJobId())
                        .map(RecJob::getTitle).orElse("the position");
                String portalUrl = "https://app.handyflow.co.za/careers/track/" + applicant.getPortalToken();
                String when = interview.getScheduledAt() != null
                        ? DATETIME_FMT.format(interview.getScheduledAt()) : "to be confirmed";
                boolean isVideo = "VIDEO".equals(interview.getInterviewType());
                String locationLine = interview.getLocation() != null && !interview.getLocation().isBlank()
                        ? "<br>" + (isVideo ? "Meeting link" : "Venue") + ": <strong>"
                        + interview.getLocation() + "</strong>"
                        : "";
                emailService.send(applicant.getEmail(),
                        "Interview rescheduled — " + jobTitle,
                        "<h2>Hi " + applicant.getFirstName() + ",</h2>"
                                + "<p>Your interview for <strong>" + jobTitle + "</strong> has been rescheduled.</p>"
                                + "<p>Reason: " + reason + "</p>"
                                + "<p>New time: <strong>" + when + "</strong>"
                                + (interview.getInterviewerName() != null
                                ? "<br>Interviewer: <strong>" + interview.getInterviewerName() + "</strong>" : "")
                                + locationLine
                                + "</p>"
                                + "<p><a href=\"" + portalUrl + "\">View your application</a></p>");
            } catch (Exception e) {
                log.warn("Failed to send interview-rescheduled email: {}", e.getMessage());
            }
        });
    }

    private void sendApplicationConfirmation(RecApplicant a, RecJob job, RecApplication app) {
        if (a.getEmail() == null) return;
        try {
            String portalUrl = "https://app.handyflow.co.za/careers/track/" + a.getPortalToken();
            emailService.send(a.getEmail(),
                    "Application received — " + job.getTitle(),
                    "<h2>Hi " + a.getFirstName() + ",</h2>"
                            + "<p>We've received your application for <strong>" + job.getTitle() + "</strong>.</p>"
                            + "<p>You can track your application status at any time using the link below:</p>"
                            + "<p><a href=\"" + portalUrl + "\" style=\"background:#1B3A6B;color:white;"
                            + "padding:12px 24px;border-radius:8px;text-decoration:none;display:inline-block\">"
                            + "Track My Application</a></p>"
                            + "<p style=\"color:#64748B;font-size:12px\">Bookmark this link — it's unique to you and doesn't require a password.</p>");
        } catch (Exception e) {
            log.warn("Failed to send application confirmation: {}", e.getMessage());
        }
    }

    private void notifyApplicantStageChange(RecApplication app, String stage, TenantId tenantId) {
        applicantRepo.findById(app.getApplicantId()).ifPresent(applicant -> {
            if (applicant.getEmail() == null) return;
            try {
                String label   = STAGE_LABELS.getOrDefault(stage, stage);
                String portalUrl = "https://app.handyflow.co.za/careers/track/" + applicant.getPortalToken();
                String jobTitle = jobRepo.findById(app.getJobId())
                        .map(RecJob::getTitle).orElse("the position");
                emailService.send(applicant.getEmail(),
                        "Application update — " + jobTitle,
                        "<h2>Hi " + applicant.getFirstName() + ",</h2>"
                                + "<p>Your application for <strong>" + jobTitle + "</strong> has been updated.</p>"
                                + "<p>Status: <strong>" + label + "</strong></p>"
                                + "<p><a href=\"" + portalUrl + "\">View your application</a></p>");
            } catch (Exception e) {
                log.warn("Failed to notify applicant of stage change: {}", e.getMessage());
            }
        });
    }

    private void notifyApplicantRejected(RecApplication app, String reason, TenantId tenantId) {
        applicantRepo.findById(app.getApplicantId()).ifPresent(applicant -> {
            if (applicant.getEmail() == null) return;
            try {
                String jobTitle = jobRepo.findById(app.getJobId())
                        .map(RecJob::getTitle).orElse("the position");
                emailService.send(applicant.getEmail(),
                        "Application outcome — " + jobTitle,
                        "<h2>Hi " + applicant.getFirstName() + ",</h2>"
                                + "<p>Thank you for your interest in <strong>" + jobTitle + "</strong>.</p>"
                                + "<p>After careful consideration, we will not be moving forward "
                                + "with your application at this time.</p>"
                                + (reason != null ? "<p>" + reason + "</p>" : "")
                                + "<p>We appreciate the time you invested and wish you the best in your search.</p>");
            } catch (Exception e) {
                log.warn("Failed to send rejection email: {}", e.getMessage());
            }
        });
    }

    private long countHiredThisMonth(TenantId tenantId) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM rec_applications
                    WHERE tenant_id = ? AND stage = 'HIRED'
                    AND hired_at >= date_trunc('month', NOW())
                    """, Long.class, tenantId.getValue());
        } catch (Exception e) { return 0; }
    }

    private String fetchTenantName(TenantId tenantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT name FROM tenants WHERE id = ?",
                    String.class, tenantId.getValue());
        } catch (Exception e) { return "HandyFlow"; }
    }

    private JobResponse toJobResponse(RecJob j) {
        return toJobResponse(j, null);
    }

    private JobResponse toJobResponse(RecJob j, String companyName) {
        return new JobResponse(j.getId(), j.getTitle(), j.getDepartment(), j.getLocation(),
                j.getJobType(), j.getExperienceLevel(), j.getDescription(),
                j.getRequirements(), j.getBenefits(),
                j.getSalaryMin(), j.getSalaryMax(), j.isShowSalary(),
                j.getStatus(), j.getSlug(), j.getClosesAt(),
                j.getApplicationCount(), companyName, j.getCreatedAt());
    }

    private ApplicationResponse toApplicationResponse(RecApplication a, boolean includeDetails) {
        RecApplicant applicant = applicantRepo.findById(a.getApplicantId()).orElse(null);
        String jobTitle = jobRepo.findById(a.getJobId()).map(RecJob::getTitle).orElse(null);

        List<InterviewResponse> interviews = includeDetails
                ? interviewRepo.findByApplicationIdOrderByScheduledAtAsc(a.getId())
                .stream().map(this::toInterviewResponse).toList()
                : List.of();
        List<StageHistoryResponse> history = includeDetails
                ? historyRepo.findByApplicationIdOrderByCreatedAtAsc(a.getId())
                .stream().map(h -> new StageHistoryResponse(
                        h.getFromStage(), h.getToStage(),
                        h.getChangedByName(), h.getNotes(), h.getCreatedAt())).toList()
                : List.of();

        String referredByUserName = a.getReferredByUserId() != null
                ? fetchUserName(a.getReferredByUserId()) : null;

        return new ApplicationResponse(
                a.getId(), a.getJobId(), jobTitle,
                a.getApplicantId(),
                applicant != null ? applicant.getFullName() : null,
                applicant != null ? applicant.getEmail() : null,
                applicant != null ? applicant.getPhone() : null,
                applicant != null && applicant.getCvUrl() != null,
                a.getStage(), a.getSource(), a.getScore(),
                a.getNotes(), a.getRejectionReason(), a.getHrEmployeeId(),
                interviews, history,
                a.getAppliedAt(), a.getStageChangedAt(), a.getHiredAt(),
                a.getOfferedSalary(), a.getOfferedSalaryFrequency(),
                a.getOfferedStartDate(), a.getOfferBenefits(), a.getOfferLetterSentAt(),
                a.getReferrerName(), a.getReferredByUserId(), referredByUserName,
                a.getReferralBonusAmount(), a.getReferralBonusStatus(), a.getReferralBonusPaidAt());
    }

    private PublicApplicationResponse toPublicResponse(RecApplication a, RecJob job,
                                                       RecApplicant applicant, String companyName) {
        String jobTitle = job != null ? job.getTitle() : "Position";
        return new PublicApplicationResponse(
                a.getId(), jobTitle, companyName,
                applicant.getFullName(), a.getStage(),
                STAGE_LABELS.getOrDefault(a.getStage(), a.getStage()),
                a.getAppliedAt(), a.getStageChangedAt());
    }

    private InterviewResponse toInterviewResponse(RecInterview i) {
        List<PanelistResponse> panelists = panelistRepo.findByInterviewId(i.getId()).stream()
                .map(p -> new PanelistResponse(p.getUserId(), p.getUserName()))
                .toList();

        String roundName = null;
        Integer roundSequence = null;
        if (i.getRoundTemplateId() != null) {
            RecJobInterviewRound round = roundRepo.findById(i.getRoundTemplateId()).orElse(null);
            if (round != null) {
                roundName = round.getName();
                roundSequence = round.getSequence();
            }
        }

        return new InterviewResponse(i.getId(), i.getInterviewType(), i.getScheduledAt(),
                i.getInterviewerName(), i.getOutcome(), i.getNotes(),
                i.getScore(), i.getLocation(), panelists,
                i.getRoundTemplateId(), roundName, roundSequence,
                i.getReminderSentAt(), i.getRescheduleReason(), i.getRescheduledFromInterviewId(),
                i.getCreatedAt());
    }
}