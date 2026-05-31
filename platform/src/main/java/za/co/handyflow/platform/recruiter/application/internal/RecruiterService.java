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

    // ── Public careers page (no auth) ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<JobResponse> getPublicJobs(TenantId tenantId) {
        return jobRepo.findOpenJobs(tenantId).stream().map(j -> {
            // Don't expose salary if show_salary = false
            if (!j.isShowSalary()) {
                return new JobResponse(j.getId(), j.getTitle(), j.getDepartment(),
                        j.getLocation(), j.getJobType(), j.getExperienceLevel(),
                        j.getDescription(), j.getRequirements(), j.getBenefits(),
                        null, null, false,
                        j.getStatus(), j.getSlug(), j.getClosesAt(),
                        j.getApplicationCount(), j.getCreatedAt());
            }
            return toJobResponse(j);
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
        return toJobResponse(job);
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

        // Upsert applicant — same person may apply to multiple jobs
        RecApplicant applicant = applicantRepo
                .findByTenantIdAndEmail(tenantId, req.email().toLowerCase())
                .orElseGet(() -> RecApplicant.create(tenantId,
                        req.firstName(), req.lastName(), req.email(),
                        req.phone(), req.location(), req.linkedinUrl(),
                        req.portfolioUrl(), req.cvBase64(), req.cvFileName()));

        // Update CV if provided
        if (req.cvBase64() != null) applicant.updateCv(req.cvBase64(), req.cvFileName());
        applicantRepo.save(applicant);

        // Check for duplicate application
        if (applicationRepo.findByJobIdAndApplicantId(jobId, applicant.getId()).isPresent()) {
            throw new HandyFlowException("You have already applied for this position",
                    HttpStatus.BAD_REQUEST, "DUPLICATE_APPLICATION");
        }

        RecApplication application = RecApplication.create(tenantId, jobId,
                applicant.getId(), req.source());
        applicationRepo.save(application);

        // Update job application count
        job.incrementApplicationCount();
        jobRepo.save(job);

        // Add stage history
        historyRepo.save(RecStageHistory.create(application.getId(),
                null, "APPLIED", applicant.getFullName(), "Application submitted"));

        // Send confirmation email to applicant
        sendApplicationConfirmation(applicant, job, application);

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
        if (req.cvBase64() != null) applicant.updateCv(req.cvBase64(), req.cvFileName());
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

    @Transactional
    public ApplicationResponse moveStage(TenantId tenantId, UUID id,
                                          MoveStageRequest req, String movedByName) {
        RecApplication app = applicationRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", id.toString()));

        String prevStage = app.getStage();

        if ("REJECTED".equals(req.stage())) {
            app.reject(req.rejectionReason());
            notifyApplicantRejected(app, req.rejectionReason(), tenantId);
        } else {
            app.moveToStage(req.stage());
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
        applicationRepo.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));
        RecInterview interview = RecInterview.create(applicationId, tenantId.getValue(),
                req.interviewType(), req.scheduledAt(),
                req.interviewerId(), req.interviewerName());
        interviewRepo.save(interview);
        return toInterviewResponse(interview);
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
                                   ConvertToEmployeeRequest req) {
        RecApplication app = applicationRepo.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId.toString()));

        RecApplicant applicant = applicantRepo.findById(app.getApplicantId())
                .orElseThrow(() -> new ResourceNotFoundException("Applicant", app.getApplicantId().toString()));

        RecJob job = jobRepo.findById(app.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job", app.getJobId().toString()));

        String jobTitle    = req.jobTitle()    != null ? req.jobTitle()    : job.getTitle();
        String department  = req.department()  != null ? req.department()  : job.getDepartment();

        // Generate next employee number: EMP-00001, EMP-00002, ...
        int seq = 1;
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ?",
                    Integer.class, tenantId.getValue());
            seq = (count != null ? count : 0) + 1;
        } catch (Exception ignored) {}
        String employeeNumber = "EMP-%05d".formatted(seq);

        // Insert HR employee via JDBC — cross-module, no domain dependency
        UUID employeeId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO hr_employees
            (id, tenant_id, employee_number, first_name, last_name, email, phone,
             job_title, department, start_date, status, employment_type,
             gross_salary, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,'ACTIVE','PERMANENT',0,NOW(),NOW())
            """,
            employeeId, tenantId.getValue(), employeeNumber,
            applicant.getFirstName(), applicant.getLastName(),
            applicant.getEmail(), applicant.getPhone(),
            jobTitle, department, req.startDate());

        app.linkToEmployee(employeeId);
        applicationRepo.save(app);

        log.info("Converted applicant={} to employee={}", applicant.getEmail(), employeeId);
        return employeeId;
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
        return new JobResponse(j.getId(), j.getTitle(), j.getDepartment(), j.getLocation(),
                j.getJobType(), j.getExperienceLevel(), j.getDescription(),
                j.getRequirements(), j.getBenefits(),
                j.getSalaryMin(), j.getSalaryMax(), j.isShowSalary(),
                j.getStatus(), j.getSlug(), j.getClosesAt(),
                j.getApplicationCount(), j.getCreatedAt());
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
                a.getAppliedAt(), a.getStageChangedAt(), a.getHiredAt());
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
        return new InterviewResponse(i.getId(), i.getInterviewType(), i.getScheduledAt(),
                i.getInterviewerName(), i.getOutcome(), i.getNotes(),
                i.getScore(), i.getCreatedAt());
    }
}
