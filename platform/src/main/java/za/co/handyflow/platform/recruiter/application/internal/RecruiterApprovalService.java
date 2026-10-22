package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.*;
import za.co.handyflow.platform.recruiter.domain.repository.RecApplicationRepository;
import za.co.handyflow.platform.recruiter.domain.repository.RecJobRepository;
import za.co.handyflow.platform.recruiter.domain.repository.RecSettingsRepository;
import za.co.handyflow.platform.recruiter.dto.RecruiterApprovalDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Approval before a job is published and before an offer letter is sent, and the company's recruiting settings. Both approvals are off
 * until a company switches them on. The rules are in {@link RecruiterApprovalRules}; this class loads, applies and saves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruiterApprovalService {

    private final RecJobRepository jobRepo;
    private final RecApplicationRepository applicationRepo;
    private final RecSettingsRepository settingsRepo;
    private final JdbcTemplate jdbc;

    // ── Settings ──────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SettingsView settingsView(TenantId tenantId) {
        RecSettings s = settings(tenantId);
        return new SettingsView(s.isRequireJobApproval(), s.isRequireOfferApproval(), s.getRetentionMonths());
    }

    @Transactional
    public SettingsView updateSettings(TenantId tenantId, SettingsRequest req, UUID by) {
        RecSettings current = settings(tenantId);
        int months = req.retentionMonths() == null ? current.getRetentionMonths() : req.retentionMonths();
        String problem = RecruiterRetentionRules.monthsProblem(months);
        if (problem != null) throw new IllegalArgumentException(problem);
        current.update(req.requireJobApproval() == null ? current.isRequireJobApproval() : req.requireJobApproval(),
                req.requireOfferApproval() == null ? current.isRequireOfferApproval() : req.requireOfferApproval(), months, by);
        settingsRepo.save(current);
        return new SettingsView(current.isRequireJobApproval(), current.isRequireOfferApproval(), current.getRetentionMonths());
    }

    RecSettings settings(TenantId tenantId) {
        return settingsRepo.findById(tenantId.getValue()).orElseGet(() -> RecSettings.defaults(tenantId.getValue()));
    }

    // ── Jobs ──────────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApprovalView jobApproval(TenantId tenantId, UUID jobId, UUID caller) {
        return jobView(tenantId, job(tenantId, jobId), caller);
    }

    @Transactional
    public ApprovalView submitJob(TenantId tenantId, UUID jobId, UUID by) {
        RecJob job = job(tenantId, jobId);
        fail(RecruiterApprovalRules.submitJobProblem(job.getStatus(), job.getApprovalStatus(), job.getTitle(), job.getDescription(),
                job.getSalaryMin(), job.getSalaryMax()));
        job.submitForApproval(by);
        jobRepo.save(job);
        log.info("Job={} sent for approval by user={}", jobId, by);
        return jobView(tenantId, job, by);
    }

    @Transactional
    public ApprovalView decideJob(TenantId tenantId, UUID jobId, DecisionRequest req, UUID by) {
        RecJob job = job(tenantId, jobId);
        boolean approve = Boolean.TRUE.equals(req.approve());
        fail(RecruiterApprovalRules.decideProblem(job.getApprovalStatus(), job.getSubmittedBy(), by, approve, req.note()));
        job.decideApproval(approve, by, req.note());
        jobRepo.save(job);
        log.info("Job={} {} by user={}", jobId, approve ? "approved" : "not approved", by);
        return jobView(tenantId, job, by);
    }

    /** Called when someone publishes a job: refused when the company needs approval and the job does not have it. */
    void requirePublishable(TenantId tenantId, RecJob job) {
        fail(RecruiterApprovalRules.publishProblem(settings(tenantId).isRequireJobApproval(), job.getApprovalStatus()));
    }

    // ── Offers ────────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApprovalView offerApproval(TenantId tenantId, UUID applicationId, UUID caller) {
        return offerView(tenantId, application(tenantId, applicationId), caller);
    }

    @Transactional
    public ApprovalView submitOffer(TenantId tenantId, UUID applicationId, UUID by) {
        RecApplication app = application(tenantId, applicationId);
        fail(RecruiterApprovalRules.submitOfferProblem(app.getStage(), app.getOfferApprovalStatus(), app.getOfferedSalary(), app.getOfferLetterSentAt() != null));
        app.submitOfferForApproval(by);
        applicationRepo.save(app);
        log.info("Offer for application={} sent for approval by user={}", applicationId, by);
        return offerView(tenantId, app, by);
    }

    @Transactional
    public ApprovalView decideOffer(TenantId tenantId, UUID applicationId, DecisionRequest req, UUID by) {
        RecApplication app = application(tenantId, applicationId);
        boolean approve = Boolean.TRUE.equals(req.approve());
        fail(RecruiterApprovalRules.decideProblem(app.getOfferApprovalStatus(), app.getOfferSubmittedBy(), by, approve, req.note()));
        app.decideOfferApproval(approve, by, req.note());
        applicationRepo.save(app);
        log.info("Offer for application={} {} by user={}", applicationId, approve ? "approved" : "not approved", by);
        return offerView(tenantId, app, by);
    }

    /** Called when someone sends an offer letter: refused when the company needs approval and the offer does not have it. */
    void requireOfferSendable(TenantId tenantId, RecApplication app) {
        fail(RecruiterApprovalRules.sendOfferProblem(settings(tenantId).isRequireOfferApproval(), app.getOfferApprovalStatus()));
    }

    // ── Views ─────────────────────────────────────────────────────────────────────────────────────

    private ApprovalView jobView(TenantId tenantId, RecJob j, UUID caller) {
        return new ApprovalView(j.getApprovalStatus(), settings(tenantId).isRequireJobApproval(), j.getSubmittedBy(), name(j.getSubmittedBy()), j.getSubmittedAt(),
                j.getApprovedBy(), name(j.getApprovedBy()), j.getApprovedAt(), j.getApprovalNote(),
                RecruiterApprovalRules.SUBMITTED.equals(j.getApprovalStatus()) && caller != null && !caller.equals(j.getSubmittedBy()));
    }

    private ApprovalView offerView(TenantId tenantId, RecApplication a, UUID caller) {
        return new ApprovalView(a.getOfferApprovalStatus(), settings(tenantId).isRequireOfferApproval(), a.getOfferSubmittedBy(), name(a.getOfferSubmittedBy()), a.getOfferSubmittedAt(),
                a.getOfferApprovedBy(), name(a.getOfferApprovedBy()), a.getOfferApprovedAt(), a.getOfferApprovalNote(),
                RecruiterApprovalRules.SUBMITTED.equals(a.getOfferApprovalStatus()) && caller != null && !caller.equals(a.getOfferSubmittedBy()));
    }

    private String name(UUID userId) {
        if (userId == null) return null;
        try {
            return jdbc.queryForObject("SELECT first_name || ' ' || last_name FROM users WHERE id = ?", String.class, userId);
        } catch (Exception e) { return null; }
    }

    private RecJob job(TenantId tenantId, UUID id) {
        return jobRepo.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new ResourceNotFoundException("Job", id.toString()));
    }

    private RecApplication application(TenantId tenantId, UUID id) {
        return applicationRepo.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new ResourceNotFoundException("Application", id.toString()));
    }

    private static void fail(String problem) { if (problem != null) throw new IllegalArgumentException(problem); }
}
