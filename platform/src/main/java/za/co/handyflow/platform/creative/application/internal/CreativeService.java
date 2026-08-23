package za.co.handyflow.platform.creative.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.domain.model.ApprovalRule;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.approvals.dto.ApprovalStepResponse;
import za.co.handyflow.platform.approvals.dto.ChainEntryInput;
import za.co.handyflow.platform.creative.domain.model.*;
import za.co.handyflow.platform.creative.domain.repository.*;
import za.co.handyflow.platform.creative.dto.*;
import za.co.handyflow.platform.shared.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FIX: backlog 1.1 — full migration of proof approval onto the shared
 * approval engine. Full cutover, per your own explicit call — not run
 * in parallel with the old CreProof/CreProofApprover token logic.
 * <p>
 * WHAT ACTUALLY CHANGED: sendProofToClient()/resolveToken()/
 * approveProofByToken()/rejectProofByToken()/getProofByToken()/
 * notifyNextApprover()/sendUnapprovedReminder() — every place that used
 * to read or write CreProof.approvalToken/tokenExpiresAt or query
 * CreProofApprover for live approval state now goes through
 * ApprovalFacade instead. SINGLE and multi-stakeholder (SEQUENTIAL/
 * PARALLEL) are now ONE code path, not two — SINGLE just degenerates to
 * a one-step SEQUENTIAL request.
 * <p>
 * WHAT DELIBERATELY DIDN'T CHANGE: configureApprovers() still writes to
 * CreProofApprover — that table is now purely a pre-send STAGING area
 * (what a staff member has configured before actually sending), not the
 * live approval-state store anymore. sendProofToClient() reads this
 * config at send time and hands it to the engine, which becomes the
 * real source of truth for everything that happens after send. This
 * hybrid means configureApprovers()/its own validation, and every job/
 * deliverable/comment method having nothing to do with approval, needed
 * zero changes — smaller, safer diff than replacing CreProofApprover
 * everywhere it's touched.
 * <p>
 * CreProof.approvalToken/tokenExpiresAt columns are left in place,
 * unused by new sends going forward (same "don't retroactively drop
 * columns" caution as every migration this session).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeService {

    private final CreJobRepository          jobRepo;
    private final CreProofRepository        proofRepo;
    private final CreProofApproverRepository approverRepo;
    private final CreProofCommentRepository commentRepo;
    private final CreDeliverableRepository  deliverableRepo;
    private final EmailService              emailService;
    private final JdbcTemplate              jdbc;
    // FIX: backlog 1.1 — the shared approval engine.
    private final ApprovalFacade            approvalFacade;

    private static final String APPROVALS_MODULE = "creative";
    private static final String APPROVALS_ENTITY_TYPE = "PROOF";

    // ── Jobs ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<JobResponse> getJobs(TenantId tenantId, String status, Pageable pageable) {
        Page<CreJob> page = jobRepo.findAll(tenantId, status, pageable);

        // FIX: batch-load proof and deliverable counts to avoid N+1
        List<UUID> jobIds = page.getContent().stream().map(CreJob::getId).toList();
        Map<UUID, Integer> proofCounts       = batchCountProofs(jobIds);
        Map<UUID, Integer> deliverableCounts = batchCountDeliverables(jobIds);

        return page.map(j -> toJobResponseCounts(j,
                proofCounts.getOrDefault(j.getId(), 0),
                deliverableCounts.getOrDefault(j.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(TenantId tenantId, UUID id) {
        return toJobResponse(findJob(tenantId, id));
    }

    @Transactional
    public JobResponse createJob(TenantId tenantId, UUID createdBy, CreateJobRequest req) {
        CreJob job = CreJob.create(
                tenantId, req.customerId(), req.clientName(), req.clientEmail(),
                req.title(), req.jobType(), req.description(), req.brief(),
                req.priority(), req.dueDate(), req.budget(), req.quotedAmount(),
                req.assignedTo(), req.notes(), createdBy);
        jobRepo.save(job);
        log.info("Created creative job={} title={}", job.getId(), req.title());
        return toJobResponse(job);
    }

    @Transactional
    public JobResponse updateJob(TenantId tenantId, UUID id, UpdateJobRequest req) {
        CreJob job = findJob(tenantId, id);
        job.updateDetails(req.title(), req.description(), req.brief(),
                req.priority(), req.dueDate(), req.budget(), req.quotedAmount(),
                req.assignedTo(), req.notes(), req.clientEmail());
        jobRepo.save(job);
        return toJobResponse(job);
    }

    @Transactional
    public JobResponse updateStatus(TenantId tenantId, UUID id, String action) {
        CreJob job = findJob(tenantId, id);
        switch (action.toUpperCase()) {
            case "START"   -> job.startWork();
            case "SEND"    -> job.sendForApproval();
            case "REVISE"  -> job.requestRevision();
            case "APPROVE" -> job.markApproved();
            case "DELIVER" -> job.markDelivered();
            case "CANCEL"  -> job.cancel();
            default -> throw new HandyFlowException(
                    "Unknown action: " + action, HttpStatus.BAD_REQUEST, "INVALID_ACTION");
        }
        jobRepo.save(job);
        return toJobResponse(job);
    }

    @Transactional
    public void deleteJob(TenantId tenantId, UUID id) {
        findJob(tenantId, id); // verify ownership
        jdbc.update("UPDATE cre_jobs SET deleted_at = NOW() WHERE id = ?", id);
        log.info("Soft-deleted creative job={}", id);
    }

    // ── Proofs ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProofResponse> getProofs(TenantId tenantId, UUID jobId) {
        findJob(tenantId, jobId);
        return proofRepo.findByJobIdOrderByVersionNumberDesc(jobId)
                .stream().map(p -> toProofResponse(p, true)).toList();
    }

    @Transactional
    public ProofResponse uploadProof(TenantId tenantId, UUID jobId,
                                     UploadProofRequest req, UUID uploadedBy) {
        CreJob job = findJob(tenantId, jobId);

        // Supersede all existing PENDING proofs
        proofRepo.findPendingByJobId(jobId).forEach(existing -> {
            existing.supersede();
            proofRepo.save(existing);
        });

        int nextVersion = proofRepo.findMaxVersion(jobId) + 1;
        CreProof proof = CreProof.create(
                jobId, tenantId.getValue(), nextVersion,
                req.title(), req.fileBase64(), req.fileName(),
                req.fileType(), req.thumbnailBase64(), req.notes(), uploadedBy);
        proofRepo.save(proof);

        if (!"AWAITING_APPROVAL".equals(job.getStatus())) {
            job.sendForApproval();
            jobRepo.save(job);
        }

        log.info("Uploaded proof v{} for job={}", nextVersion, jobId);
        return toProofResponse(proof, true);
    }

    /**
     * Opts a PENDING, not-yet-sent proof into multi-stakeholder approval.
     * Restricted to proofs that haven't been sent yet (sentAt == null) —
     * changing the approver list on a proof that's already mid-flight would
     * create confusing, hard-to-reason-about state (an approver who already
     * clicked a link that no longer matches the configured chain).
     * Idempotent: re-calling this replaces the previous approver list
     * entirely, so staff can freely adjust the list before actually
     * sending.
     * <p>
     * UNCHANGED by the backlog 1.1 migration — see this class's own
     * Javadoc for why CreProofApprover stays as pre-send staging data.
     */
    @Transactional
    public ProofResponse configureApprovers(TenantId tenantId, UUID jobId, UUID proofId,
                                            ConfigureApprovalRequest req) {
        findJob(tenantId, jobId);
        CreProof proof = proofRepo.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Proof", proofId.toString()));

        if (!proof.isPending()) {
            throw new HandyFlowException(
                    "Only PENDING proofs can have their approval chain configured",
                    HttpStatus.BAD_REQUEST, "PROOF_NOT_PENDING");
        }
        if (proof.getSentAt() != null) {
            throw new HandyFlowException(
                    "This proof has already been sent — the approval chain can't be changed now. " +
                            "Upload a new version if the approvers need to change.",
                    HttpStatus.BAD_REQUEST, "PROOF_ALREADY_SENT");
        }

        approverRepo.deleteByProofId(proofId);

        int order = 1;
        for (AddApproverRequest a : req.approvers()) {
            approverRepo.save(CreProofApprover.create(
                    proofId, tenantId.getValue(), a.approverName(), a.approverEmail(), order++));
        }

        proof.configureApprovalMode(req.mode());
        proofRepo.save(proof);

        log.info("Configured proof={} for {} approval with {} approver(s)",
                proofId, req.mode(), req.approvers().size());

        return toProofResponse(proof, true);
    }

    /**
     * FIX: backlog 1.1 — full redesign. SINGLE and multi-stakeholder are
     * now one path: SINGLE is simply a one-entry SEQUENTIAL chain, which
     * degenerates to identical observable behaviour (one email, one
     * link, done). Reads CreProofApprover only as pre-send config (see
     * class Javadoc); the engine becomes the source of truth for
     * everything from this point forward.
     */
    @Transactional
    public ProofResponse sendProofToClient(TenantId tenantId, UUID jobId,
                                           UUID proofId, SendProofRequest req) {
        findJob(tenantId, jobId);
        CreProof proof = proofRepo.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Proof", proofId.toString()));

        if (!proof.isPending()) {
            throw new HandyFlowException(
                    "Only PENDING proofs can be sent for approval",
                    HttpStatus.BAD_REQUEST, "PROOF_NOT_PENDING");
        }

        List<ChainEntryInput> chain;
        ApprovalRule.ApprovalMode mode;
        String allEmailsForRecord;

        if ("SINGLE".equals(proof.getApprovalMode())) {
            mode = ApprovalRule.ApprovalMode.SEQUENTIAL;
            chain = List.of(new ChainEntryInput("EXTERNAL_CONTACT", req.email(), null, false));
            allEmailsForRecord = req.email();
        } else {
            List<CreProofApprover> configured = approverRepo.findByProofIdOrderByApprovalOrderAsc(proofId);
            if (configured.isEmpty()) {
                throw new HandyFlowException(
                        "This proof is set to " + proof.getApprovalMode() + " approval but has no approvers " +
                                "configured — add approvers before sending.",
                        HttpStatus.BAD_REQUEST, "NO_APPROVERS_CONFIGURED");
            }
            // "PARALLEL" in this module's own vocabulary always meant
            // "every approver must approve" (the old allApproved check) —
            // that's PARALLEL_ALL in the engine's vocabulary, not
            // PARALLEL_ANY_ONE.
            mode = "SEQUENTIAL".equals(proof.getApprovalMode())
                    ? ApprovalRule.ApprovalMode.SEQUENTIAL
                    : ApprovalRule.ApprovalMode.PARALLEL_ALL;
            chain = configured.stream()
                    .map(a -> new ChainEntryInput("EXTERNAL_CONTACT", a.getApproverEmail(), a.getApproverName(), false))
                    .toList();
            allEmailsForRecord = configured.stream()
                    .map(CreProofApprover::getApproverEmail)
                    .collect(Collectors.joining(", "));
        }

        ApprovalRequestResponse approvalReq = approvalFacade.submitAdHoc(
                tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, proofId, null, mode, chain, Map.of());

        proof.linkApprovalRequest(approvalReq.id());

        String tenantName = fetchTenantName(tenantId);

        // SEQUENTIAL: only step 1 is emailed now — later steps are emailed
        // one at a time as each prior one is actioned, in
        // notifyNextApprover(). PARALLEL_ALL: every step is emailed at
        // once — matches the old "every approver gets sentAt set at once,
        // when the proof is sent" behaviour exactly.
        List<ApprovalStepResponse> stepsToEmailNow = mode == ApprovalRule.ApprovalMode.SEQUENTIAL
                ? approvalReq.steps().stream().filter(s -> s.stepOrder() == 1).toList()
                : approvalReq.steps();

        int sent = 0;
        for (ApprovalStepResponse step : stepsToEmailNow) {
            try {
                sendApprovalStepEmail(tenantName, proof, step, mode, req.message());
                sent++;
            } catch (Exception e) {
                // Same "one failed email must not block the others, or
                // corrupt state" reasoning the original PARALLEL send loop
                // already had — a step this failed on is simply not
                // reachable by its intended approver yet; nothing else
                // about the request's state is affected.
                log.error("Failed to send approval email to step={} ({}) for proof={}: {}",
                        step.id(), step.approverValue(), proofId, e.getMessage(), e);
            }
        }

        if (sent == 0) {
            throw new HandyFlowException(
                    "Failed to send to any approver — check the configured email addresses.",
                    HttpStatus.INTERNAL_SERVER_ERROR, "EMAIL_FAILED");
        }

        proof.markSent(allEmailsForRecord);
        proofRepo.save(proof);

        log.info("Sent proof={} for {} approval — {} of {} step(s) notified",
                proofId, proof.getApprovalMode(), sent, stepsToEmailNow.size());

        return toProofResponse(proof, true);
    }

    /**
     * FIX: backlog 1.1 — dispatches to the right email, using
     * buildApprovalEmail() for the SINGLE-mode/first-of-one case
     * (unchanged content from before) and the modified
     * buildApproverEmail() (now taking primitives, not a CreProofApprover
     * entity — see that method's own comment) for every other case.
     */
    private void sendApprovalStepEmail(String tenantName, CreProof proof, ApprovalStepResponse step,
                                       ApprovalRule.ApprovalMode mode, String customMessage) {
        String approvalUrl = "https://app.handyflow.co.za/creative/approve/" + step.publicToken();
        if ("SINGLE".equals(proof.getApprovalMode())) {
            String subject = tenantName + " — Please review and approve your proof";
            String html = buildApprovalEmail(tenantName, proof, approvalUrl, customMessage);
            emailService.send(step.approverValue(), subject, html);
        } else {
            sendApproverEmail(tenantName, proof, step.approverName(), step.approverValue(),
                    step.stepOrder(), mode, approvalUrl, customMessage);
        }
    }

    @Transactional
    public ProofResponse addTeamComment(TenantId tenantId, UUID jobId,
                                        UUID proofId, AddCommentRequest req,
                                        String teamMemberName) {
        findJob(tenantId, jobId);
        CreProof proof = proofRepo.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Proof", proofId.toString()));
        commentRepo.save(CreProofComment.create(
                proofId, tenantId.getValue(), teamMemberName, "TEAM", req.comment(),
                req.timecodeSeconds(), req.anchorX(), req.anchorY()));
        return toProofResponse(proof, true);
    }

    // ── Public proof approval (no auth) ──────────────────────────────────────

    @Transactional
    public PublicProofResponse getProofByToken(String token) {
        TokenResolution res = resolveToken(token);
        CreProof proof = res.proof();

        CreJob job = jobRepo.findById(proof.getJobId())
                .orElseThrow(() -> new HandyFlowException("Job not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        String tenantName = fetchTenantName(job.getTenantId());
        List<CommentResponse> comments = commentRepo.findByProofIdOrderByCreatedAtAsc(proof.getId())
                .stream().map(this::toCommentResponse).toList();

        // NEW: first-view notification — the "client has seen it, why
        // haven't they responded" signal the gap analysis flagged as
        // missing. markViewedIfFirstTime() is a no-op on every call after
        // the first, so repeat views (client re-opening the link) never
        // re-notify. Wrapped so a notification failure can never prevent
        // the client from actually viewing their proof — the one thing this
        // endpoint absolutely must not break.
        if (proof.markViewedIfFirstTime()) {
            proofRepo.save(proof);
            try {
                notifyTeam(job, "Proof viewed: " + job.getTitle(),
                        buildProofViewedEmail(job.getTitle(), proof.getVersionNumber(), job.getClientName()));
            } catch (Exception e) {
                log.error("Failed to send proof-viewed notification for proof={}: {}",
                        proof.getId(), e.getMessage(), e);
            }
        }

        // FIX: backlog 1.1 — multi-stakeholder context now comes from the
        // engine's own request/steps, not CreProofApprover. Empty/null
        // for a request with only one step (SINGLE-equivalent).
        String myApproverName = null;
        List<PublicProofResponse.ApproverSummary> others = List.of();
        if (res.request() != null && res.request().steps().size() > 1) {
            myApproverName = res.step().approverName();
            others = res.request().steps().stream()
                    .map(s -> new PublicProofResponse.ApproverSummary(
                            s.approverName(), s.stepOrder(), s.status()))
                    .toList();
        }

        return new PublicProofResponse(
                proof.getId(), job.getTitle(), job.getClientName(), tenantName,
                proof.getVersionNumber(), proof.getTitle(),
                proof.getFileUrl(), proof.getThumbnailUrl(),
                proof.getFileName(), proof.getFileType(),
                proof.getStatus(), comments, proof.getCreatedAt(),
                proof.getApprovalMode(), myApproverName, others);
    }

    /**
     * FIX: backlog 1.1 — replaced entirely. actOnPublicStep() now owns
     * PENDING/ordering/outcome-resolution (identical logic to the
     * internal actOnStep() path — see ApprovalEngineService's own
     * performAction()); this method's job shrinks to recording the
     * result onto CreProof's own fields (still read directly by the
     * approval-certificate PDF and ProofResponse.approvedByName) and
     * driving the same notify-next-approver / complete-the-job behaviour
     * as before.
     */
    @Transactional
    public void approveProofByToken(String token, ApproveProofRequest req, String clientIp) {
        TokenResolution res = resolveToken(token);
        CreProof proof = res.proof();

        ApprovalRequestResponse updated = approvalFacade.actOnPublicStep(token, "APPROVE", null, clientIp);
        ApprovalStepResponse myStep = updated.steps().stream()
                .filter(s -> token.equals(s.publicToken())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Acted-on step vanished from its own request response"));

        String approverName  = "SINGLE".equals(proof.getApprovalMode()) ? req.clientName()  : myStep.approverName();
        String approverEmail = "SINGLE".equals(proof.getApprovalMode()) ? req.clientEmail() : myStep.approverValue();

        if ("APPROVED".equals(updated.status())) {
            proof.approve(approverName, approverEmail, clientIp);
            proofRepo.save(proof);
            completeApproval(proof);
            log.info("Proof={} fully approved via engine, completing approver={}", proof.getId(), approverName);
        } else {
            // Still IN_PROGRESS — SEQUENTIAL only (PARALLEL_ALL has
            // nothing further to notify; every step was already emailed
            // when the proof was sent).
            ApprovalRule.ApprovalMode mode = updated.approvalMode() != null
                    ? ApprovalRule.ApprovalMode.valueOf(updated.approvalMode()) : null;
            if (mode == ApprovalRule.ApprovalMode.SEQUENTIAL) {
                updated.steps().stream()
                        .filter(s -> s.stepOrder() == myStep.stepOrder() + 1)
                        .findFirst()
                        .ifPresent(next -> notifyNextApprover(proof, next, mode));
            }
            log.info("Proof={} approver={} approved ip={} — request still in progress",
                    proof.getId(), approverName, clientIp);
        }
    }

    private void completeApproval(CreProof proof) {
        jobRepo.findById(proof.getJobId()).ifPresent(job -> {
            job.markApproved();
            jobRepo.save(job);
            log.info("Proof={} fully approved", proof.getId());
        });
    }

    /**
     * FIX: backlog 1.1 — signature changed from
     * (CreJob, CreProof, CreProofApprover) to (CreProof, ApprovalStepResponse,
     * ApprovalRule.ApprovalMode) — the entity this used to take no longer
     * exists in the live-approval-state sense (see class Javadoc).
     * job.getTenantId()'s original role (resolving tenantName safely,
     * since CreProof stores tenantId as a raw UUID) is replaced by loading
     * the job fresh here — same safety, one fewer parameter to thread through.
     */
    private void notifyNextApprover(CreProof proof, ApprovalStepResponse next, ApprovalRule.ApprovalMode mode) {
        try {
            CreJob job = jobRepo.findById(proof.getJobId()).orElse(null);
            if (job == null) return;
            String tenantName = fetchTenantName(job.getTenantId());
            String approvalUrl = "https://app.handyflow.co.za/creative/approve/" + next.publicToken();
            sendApproverEmail(tenantName, proof, next.approverName(), next.approverValue(),
                    next.stepOrder(), mode, approvalUrl, null);
        } catch (Exception e) {
            log.error("Failed to notify next approver={} for proof={}: {}",
                    next.approverValue(), proof.getId(), e.getMessage(), e);
        }
    }

    /**
     * FIX: backlog 1.1 — replaced entirely, same shape as approveProofByToken().
     */
    @Transactional
    public void rejectProofByToken(String token, RejectProofRequest req) {
        TokenResolution res = resolveToken(token);
        CreProof proof = res.proof();
        String rejectorName = res.step() != null && res.step().approverName() != null
                ? res.step().approverName() : "Client";

        // Same DELIBERATE DESIGN CHOICE as before this migration: any
        // single approver rejecting stops the whole chain immediately —
        // resolveRequestOutcome() inside the engine already enforces this
        // for SEQUENTIAL/PARALLEL_ALL (any reject = whole request rejected).
        approvalFacade.actOnPublicStep(token, "REJECT", req.reason(), null);

        proof.reject(req.reason());
        proofRepo.save(proof);

        commentRepo.save(CreProofComment.create(
                proof.getId(), proof.getTenantId(),
                rejectorName, "CLIENT", "Changes requested: " + req.reason(), null, null, null));

        jobRepo.findById(proof.getJobId()).ifPresent(job -> {
            job.requestRevision();
            jobRepo.save(job);
            try {
                notifyTeam(job, "Changes requested: " + job.getTitle(),
                        buildRejectionNotificationEmail(job.getTitle(), proof.getVersionNumber(), req.reason()));
            } catch (Exception e) {
                log.error("Failed to send rejection notification for proof={}: {}",
                        proof.getId(), e.getMessage(), e);
            }
        });

        log.info("Proof={} rejected by {}: {}", proof.getId(), rejectorName, req.reason());
    }

    @Transactional
    public void addClientComment(String token, AddCommentRequest req) {
        CreProof proof = resolveToken(token).proof();
        String authorName = req.authorName() != null ? req.authorName() : "Client";
        commentRepo.save(CreProofComment.create(
                proof.getId(), proof.getTenantId(), authorName, "CLIENT", req.comment(),
                req.timecodeSeconds(), req.anchorX(), req.anchorY()));

        // NEW: previously nobody was told a client had commented at all —
        // the comment just sat in the thread until someone happened to
        // check.
        jobRepo.findById(proof.getJobId()).ifPresent(job -> {
            try {
                notifyTeam(job, "New comment: " + job.getTitle(),
                        buildClientCommentEmail(job.getTitle(), proof.getVersionNumber(), authorName,
                                req.comment(), req.timecodeSeconds()));
            } catch (Exception e) {
                log.error("Failed to send client-comment notification for proof={}: {}",
                        proof.getId(), e.getMessage(), e);
            }
        });
    }

    // ── Deliverables ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DeliverableResponse> getDeliverables(TenantId tenantId, UUID jobId) {
        findJob(tenantId, jobId);
        return deliverableRepo.findByJobIdOrderByCreatedAtDesc(jobId)
                .stream().map(this::toDeliverableResponse).toList();
    }

    @Transactional
    public DeliverableResponse addDeliverable(TenantId tenantId, UUID jobId,
                                              AddDeliverableRequest req, UUID uploadedBy) {
        CreJob job = findJob(tenantId, jobId);
        CreDeliverable d = CreDeliverable.create(
                jobId, tenantId.getValue(),
                req.fileBase64(), req.fileName(), req.fileType(),
                req.fileSize(), req.notes(), uploadedBy);
        deliverableRepo.save(d);

        if ("APPROVED".equals(job.getStatus())) {
            job.markDelivered();
            jobRepo.save(job);
        }

        // NEW: previously nobody outside HandyFlow was ever told a
        // deliverable existed — the client had no way to know their final
        // files were ready short of asking.
        if (job.getClientEmail() != null && !job.getClientEmail().isBlank()) {
            try {
                String tenantName = fetchTenantName(tenantId);
                emailService.send(job.getClientEmail(),
                        tenantName + " — Your files are ready: " + job.getTitle(),
                        buildDeliverableReadyEmail(tenantName, job.getTitle(), d.getFileName()));
            } catch (Exception e) {
                log.error("Failed to send deliverable-ready notification for job={}: {}",
                        jobId, e.getMessage(), e);
            }
        }

        return toDeliverableResponse(d);
    }

    // ── Proof file access (for staff preview / version comparison) ──────────

    public record ProofFile(byte[] content, String contentType, String fileName) {}

    @Transactional(readOnly = true)
    public ProofFile getProofFile(TenantId tenantId, UUID jobId, UUID proofId) {
        findJob(tenantId, jobId); // verifies tenant ownership before releasing any file content
        CreProof proof = proofRepo.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Proof", proofId.toString()));
        if (proof.getFileUrl() == null || proof.getFileUrl().isBlank()) {
            throw new ResourceNotFoundException("Proof file", proofId.toString());
        }
        byte[] content = java.util.Base64.getDecoder().decode(proof.getFileUrl());
        String contentType = proof.getFileType() != null && !proof.getFileType().isBlank()
                ? proof.getFileType() : "application/octet-stream";
        return new ProofFile(content, contentType, proof.getFileName());
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CreativeSummaryResponse getSummary(TenantId tenantId) {
        return new CreativeSummaryResponse(
                jobRepo.countByStatus(tenantId, "BRIEFING"),
                jobRepo.countByStatus(tenantId, "IN_PROGRESS"),
                jobRepo.countByStatus(tenantId, "AWAITING_APPROVAL"),
                jobRepo.countByStatus(tenantId, "IN_REVISION"),
                jobRepo.countByStatus(tenantId, "APPROVED"),
                jobRepo.countByStatus(tenantId, "DELIVERED"),
                countOverdue(tenantId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CreJob findJob(TenantId tenantId, UUID id) {
        return jobRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", id.toString()));
    }

    /**
     * FIX: backlog 1.1 — replaces the old (CreProof, CreProofApprover)
     * shape. step is null only when the resolved request had exactly one
     * step and that step's own identity isn't separately meaningful — in
     * practice this never actually happens (step is always populated,
     * since every request has at least one step), kept nullable only to
     * mirror how "approver == null meant SINGLE mode" worked before,
     * without over-claiming a distinction the engine doesn't actually make.
     */
    private record TokenResolution(CreProof proof, ApprovalStepResponse step, ApprovalRequestResponse request) {}

    /**
     * FIX: backlog 1.1 — replaced entirely. Genuinely simpler than
     * before: no more "try the proof's own token, then try an
     * approver's" two-step lookup — every approval, SINGLE or
     * multi-stakeholder, is step-based now, so there's exactly one
     * lookup path. SEQUENTIAL ordering enforcement ("not your turn yet")
     * moved into the engine's own performAction() — see
     * ApprovalEngineService — so it isn't duplicated here anymore either.
     */
    private TokenResolution resolveToken(String token) {
        ApprovalRequestResponse request = approvalFacade.getRequestByStepToken(token)
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid or expired approval link", HttpStatus.BAD_REQUEST, "INVALID_TOKEN"));
        ApprovalStepResponse step = request.steps().stream()
                .filter(s -> token.equals(s.publicToken()))
                .findFirst()
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid or expired approval link", HttpStatus.BAD_REQUEST, "INVALID_TOKEN"));

        if ("REJECTED".equals(step.status()) || "REJECTED".equals(request.status())) {
            throw new HandyFlowException(
                    "This proof has been rejected — no further action is needed.",
                    HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
        }
        if ("APPROVED".equals(step.status())) {
            throw new HandyFlowException(
                    "You have already approved this proof.", HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
        }
        if (!"PENDING".equals(step.status())) {
            throw new HandyFlowException(
                    "This approval link has expired. Ask your designer to resend it.",
                    HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
        }

        CreProof proof = proofRepo.findById(request.entityId())
                .orElseThrow(() -> new HandyFlowException("Proof not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        return new TokenResolution(proof, step, request);
    }

    /**
     * FIX: original toJobResponse() called findByJobIdOrderByVersionNumberDesc().size()
     * and findByJobIdOrderByCreatedAtDesc().size() for every job — N+1 queries.
     * Now both counts are batched via GROUP BY queries before the page map.
     */
    private Map<UUID, Integer> batchCountProofs(List<UUID> jobIds) {
        if (jobIds.isEmpty()) return Map.of();
        String inClause = jobIds.stream().map(UUID::toString)
                .collect(Collectors.joining("','", "'", "'"));
        return jdbc.queryForList(
                        "SELECT job_id, COUNT(*) as cnt FROM cre_proofs WHERE job_id IN (" + inClause + ") GROUP BY job_id")
                .stream().collect(Collectors.toMap(
                        row -> UUID.fromString(row.get("job_id").toString()),
                        row -> ((Number) row.get("cnt")).intValue()));
    }

    private Map<UUID, Integer> batchCountDeliverables(List<UUID> jobIds) {
        if (jobIds.isEmpty()) return Map.of();
        String inClause = jobIds.stream().map(UUID::toString)
                .collect(Collectors.joining("','", "'", "'"));
        return jdbc.queryForList(
                        "SELECT job_id, COUNT(*) as cnt FROM cre_deliverables WHERE job_id IN (" + inClause + ") GROUP BY job_id")
                .stream().collect(Collectors.toMap(
                        row -> UUID.fromString(row.get("job_id").toString()),
                        row -> ((Number) row.get("cnt")).intValue()));
    }

    private long countOverdue(TenantId tenantId) {
        try {
            Long n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM cre_jobs " +
                            "WHERE tenant_id = ? AND deleted_at IS NULL " +
                            "AND status NOT IN ('APPROVED','DELIVERED','INVOICED','CANCELLED') " +
                            "AND due_date < CURRENT_DATE",
                    Long.class, tenantId.getValue());
            return n != null ? n : 0;
        } catch (Exception e) { return 0; }
    }

    private String fetchTenantName(TenantId tenantId) {
        try {
            return jdbc.queryForObject("SELECT name FROM tenants WHERE id = ?",
                    String.class, tenantId.getValue());
        } catch (Exception e) { return "HandyFlow"; }
    }

    /**
     * FIX: was hardcoded to "Team Member" on CreativeController directly,
     * with a comment claiming it resolved from the DB when it never did —
     * every team comment was attributed to the same fake name regardless of
     * who actually wrote it. Moved here (this class already has the jdbc
     * dependency and the identical pattern in fetchTenantName above) rather
     * than adding a new dependency to the controller. Falls back to "Team
     * Member" only if the user genuinely can't be found — not as the
     * default outcome.
     */
    public String fetchUserName(UUID userId) {
        if (userId == null) return "Team Member";
        try {
            return jdbc.queryForObject(
                    "SELECT TRIM(CONCAT(first_name, ' ', last_name)) FROM users WHERE id = ?",
                    String.class, userId);
        } catch (Exception e) {
            return "Team Member";
        }
    }

    /** Returns null (not a placeholder) if no email is found — callers must handle that explicitly. */
    private String fetchUserEmail(UUID userId) {
        if (userId == null) return null;
        try {
            return jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves who's actually responsible for a job (assignedTo, falling
     * back to createdBy) and emails them — the shared resolution logic
     * behind every "notify the team" call site in this class (rejection,
     * new client comment, proof viewed). Logs clearly rather than silently
     * doing nothing when nobody notifiable can be found, matching the same
     * "log rather than fake success" principle used in
     * ContractExpiryScheduler for the analogous gap.
     */
    private void notifyTeam(CreJob job, String subject, String htmlBody) {
        UUID recipientId = job.getAssignedTo() != null ? job.getAssignedTo() : job.getCreatedBy();
        String recipientEmail = fetchUserEmail(recipientId);
        if (recipientEmail != null) {
            emailService.send(recipientEmail, subject, htmlBody);
        } else {
            log.warn("No notifiable email found for job={} (assignedTo={}, createdBy={}) — " +
                            "'{}' notification was not actually sent to anyone",
                    job.getId(), job.getAssignedTo(), job.getCreatedBy(), subject);
        }
    }

    // ── Scheduled notifications ─────────────────────────────────────────────
    // Called by CreativeNotificationScheduler — kept here rather than in the
    // scheduler itself so all of this module's email content and recipient
    // resolution logic stays in one place, matching the existing convention
    // in this class (buildApprovalEmail, notifyTeam, etc.) rather than
    // splitting it across two classes.

    /**
     * FIX: backlog 1.1 — was building its reminder link from
     * proof.getApprovalToken(), which no longer gets populated by
     * sendProofToClient() under the new flow and would have sent a
     * genuinely broken link. Now resolves the CURRENT pending step (the
     * one actually awaiting action right now) via the engine and links
     * to that step's real token instead.
     */
    @Transactional
    public void sendUnapprovedReminder(UUID proofId) {
        CreProof proof = proofRepo.findById(proofId).orElse(null);
        // Already handled (or gone) — scheduler may have a slightly stale
        // view of the world between query and processing; tolerate it.
        if (proof == null || proof.getReminderSentAt() != null) return;
        CreJob job = jobRepo.findById(proof.getJobId()).orElse(null);
        if (job == null) return;

        proof.markReminderSent();
        proofRepo.save(proof);

        if (proof.getSentToEmail() == null || proof.getSentToEmail().isBlank()) {
            log.warn("Proof={} needs an unapproved reminder but has no sentToEmail on record", proofId);
            return;
        }

        Optional<ApprovalRequestResponse> current = proof.getApprovalRequestId() != null
                ? approvalFacade.getLatestRequestForEntity(job.getTenantId(), APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, proofId)
                : Optional.empty();
        Optional<ApprovalStepResponse> pendingStep = current
                .flatMap(r -> r.steps().stream().filter(s -> "PENDING".equals(s.status())).findFirst());

        if (pendingStep.isEmpty()) {
            log.warn("Proof={} needs an unapproved reminder but has no current pending approval step — " +
                    "cannot build a working link", proofId);
            return;
        }

        try {
            String tenantName  = fetchTenantName(job.getTenantId());
            String approvalUrl = "https://app.handyflow.co.za/creative/approve/" + pendingStep.get().publicToken();
            emailService.send(proof.getSentToEmail(),
                    tenantName + " — Reminder: your proof is awaiting review",
                    buildUnapprovedReminderEmail(tenantName, job.getTitle(), proof.getVersionNumber(), approvalUrl));
        } catch (Exception e) {
            log.error("Failed to send unapproved-proof reminder for proof={}: {}", proofId, e.getMessage(), e);
        }
    }

    @Transactional
    public void sendOverdueAlert(UUID jobId) {
        CreJob job = jobRepo.findById(jobId).orElse(null);
        if (job == null || job.getOverdueAlertSentAt() != null) return;

        job.markOverdueAlertSent();
        jobRepo.save(job);

        try {
            notifyTeam(job, "Overdue: " + job.getTitle(),
                    buildOverdueAlertEmail(job.getTitle(), job.getDueDate()));
        } catch (Exception e) {
            log.error("Failed to send overdue alert for job={}: {}", jobId, e.getMessage(), e);
        }
    }

    private String buildRejectionNotificationEmail(String jobTitle, int versionNumber, String reason) {
        String reasonHtml = reason != null && !reason.isBlank()
                ? org.springframework.web.util.HtmlUtils.htmlEscape(reason)
                : "No specific reason was provided.";
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"></head>
            <body style="font-family:Arial,sans-serif;background:#F1F5F9;margin:0;padding:0;">
              <div style="max-width:560px;margin:40px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                <div style="background:#1B3A6B;padding:28px 32px;">
                  <h1 style="color:#fff;margin:0;font-size:20px">Changes Requested</h1>
                  <p style="color:rgba(255,255,255,0.7);margin:4px 0 0;font-size:13px">Creative Studio — Proof Review</p>
                </div>
                <div style="padding:32px;">
                  <p style="color:#374151;font-size:14px;line-height:1.6">
                    The client has requested changes on <strong>%s</strong> (Version %d).
                  </p>
                  <div style="background:#FEF2F2;border-left:3px solid #DC2626;padding:12px 16px;border-radius:0 8px 8px 0;">
                    <p style="margin:0;color:#991B1B;font-size:13px;line-height:1.6;">%s</p>
                  </div>
                  <p style="color:#64748B;font-size:13px;margin-top:20px;">
                    Log into HandyFlow to review the full feedback and upload a revised version.
                  </p>
                </div>
              </div>
            </body></html>
            """.formatted(jobTitle, versionNumber, reasonHtml);
    }

    private String buildProofViewedEmail(String jobTitle, int versionNumber, String clientName) {
        return simpleNoticeEmail("Proof Viewed",
                org.springframework.web.util.HtmlUtils.htmlEscape(clientName)
                        + " has opened <strong>" + org.springframework.web.util.HtmlUtils.htmlEscape(jobTitle)
                        + "</strong> (Version " + versionNumber + ").",
                "No action needed — this is just a signal that they've seen it.");
    }

    private String buildClientCommentEmail(String jobTitle, int versionNumber, String authorName,
                                           String comment, Double timecodeSeconds) {
        String timecodeNote = timecodeSeconds != null
                ? " at " + formatTimecode(timecodeSeconds) : "";
        return simpleNoticeEmail("New Comment",
                "<strong>" + org.springframework.web.util.HtmlUtils.htmlEscape(authorName) + "</strong> commented on "
                        + org.springframework.web.util.HtmlUtils.htmlEscape(jobTitle)
                        + " (Version " + versionNumber + ")" + timecodeNote + ":",
                "\u201c" + org.springframework.web.util.HtmlUtils.htmlEscape(comment) + "\u201d");
    }

    private String formatTimecode(double seconds) {
        int total = (int) Math.round(seconds);
        return String.format("%d:%02d", total / 60, total % 60);
    }

    private String buildDeliverableReadyEmail(String tenantName, String jobTitle, String fileName) {
        return simpleNoticeEmail("Your Files Are Ready",
                "Your final files for <strong>" + org.springframework.web.util.HtmlUtils.htmlEscape(jobTitle)
                        + "</strong> have been delivered:",
                org.springframework.web.util.HtmlUtils.htmlEscape(fileName)
                        + "<br/><br/>Log into HandyFlow to download your files, or contact "
                        + org.springframework.web.util.HtmlUtils.htmlEscape(tenantName) + " directly.");
    }

    private String buildUnapprovedReminderEmail(String tenantName, String jobTitle, int versionNumber, String approvalUrl) {
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"></head>
            <body style="font-family:Arial,sans-serif;background:#F1F5F9;margin:0;padding:0;">
              <div style="max-width:560px;margin:40px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                <div style="background:#1B3A6B;padding:28px 32px;">
                  <h1 style="color:#fff;margin:0;font-size:20px">%s</h1>
                  <p style="color:rgba(255,255,255,0.7);margin:4px 0 0;font-size:13px">Creative Studio — Proof Review</p>
                </div>
                <div style="padding:32px;">
                  <p style="color:#374151;font-size:14px;line-height:1.6">
                    Just a reminder — your proof for <strong>%s</strong> (Version %d) is still
                    awaiting your review.
                  </p>
                  <p style="text-align:center;margin:28px 0;">
                    <a href="%s" style="background:#1B3A6B;color:#fff;padding:14px 32px;border-radius:8px;text-decoration:none;font-weight:700;font-size:16px;display:inline-block;">
                      Review &amp; Approve Proof
                    </a>
                  </p>
                </div>
              </div>
            </body></html>
            """.formatted(tenantName, jobTitle, versionNumber, approvalUrl);
    }

    private String buildOverdueAlertEmail(String jobTitle, LocalDate dueDate) {
        return simpleNoticeEmail("Job Overdue",
                "<strong>" + org.springframework.web.util.HtmlUtils.htmlEscape(jobTitle)
                        + "</strong> was due on " + dueDate + " and is still not APPROVED/DELIVERED.",
                "Log into HandyFlow to check its status.");
    }

    /** Shared minimal layout for the shorter internal-notification emails, matching this class's existing visual style. */
    private String simpleNoticeEmail(String heading, String bodyLine1, String bodyLine2) {
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"></head>
            <body style="font-family:Arial,sans-serif;background:#F1F5F9;margin:0;padding:0;">
              <div style="max-width:560px;margin:40px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                <div style="background:#1B3A6B;padding:28px 32px;">
                  <h1 style="color:#fff;margin:0;font-size:20px">%s</h1>
                  <p style="color:rgba(255,255,255,0.7);margin:4px 0 0;font-size:13px">Creative Studio</p>
                </div>
                <div style="padding:32px;">
                  <p style="color:#374151;font-size:14px;line-height:1.6">%s</p>
                  <p style="color:#64748B;font-size:13px;line-height:1.6;margin-top:12px;">%s</p>
                </div>
              </div>
            </body></html>
            """.formatted(heading, bodyLine1, bodyLine2);
    }

    private String buildApprovalEmail(String tenantName, CreProof proof,
                                      String approvalUrl, String customMessage) {
        String msg = customMessage != null && !customMessage.isBlank()
                ? "<p style=\"background:#FFFBEB;border-left:3px solid #D97706;padding:12px 16px;border-radius:0 8px 8px 0;\">"
                + customMessage + "</p>" : "";
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"></head>
            <body style="font-family:Arial,sans-serif;background:#F1F5F9;margin:0;padding:0;">
              <div style="max-width:560px;margin:40px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                <div style="background:#1B3A6B;padding:28px 32px;">
                  <h1 style="color:#fff;margin:0;font-size:20px">%s</h1>
                  <p style="color:rgba(255,255,255,0.7);margin:4px 0 0;font-size:13px">Creative Studio — Proof Review</p>
                </div>
                <div style="padding:32px;">
                  <p style="color:#374151;font-size:14px;line-height:1.6">Hi,</p>
                  <p style="color:#374151;font-size:14px;line-height:1.6">
                    Your proof (Version %d) is ready for your review. Please click the button below to view it and share your feedback.
                  </p>
                  %s
                  <p style="text-align:center;margin:28px 0;">
                    <a href="%s" style="background:#1B3A6B;color:#fff;padding:14px 32px;border-radius:8px;text-decoration:none;font-weight:700;font-size:16px;display:inline-block;">
                      Review &amp; Approve Proof
                    </a>
                  </p>
                  <p style="color:#64748B;font-size:13px;">
                    You can also approve or request changes directly from this link.<br>
                    No HandyFlow account is required — the link opens directly in your browser.<br><br>
                    <strong>This link expires in 30 days.</strong>
                  </p>
                  <div style="background:#F8FAFC;border:1px solid #E2E8F0;border-radius:8px;padding:12px 16px;margin-top:20px;">
                    <p style="color:#94A3B8;font-size:11px;margin:0;line-height:1.7;">
                      If the button does not work, copy this link into your browser:<br>
                      <a href="%s" style="color:#0D9488;word-break:break-all;">%s</a>
                    </p>
                  </div>
                </div>
                <div style="background:#F8FAFC;padding:20px 32px;border-top:1px solid #E2E8F0;">
                  <p style="color:#94A3B8;font-size:12px;margin:0;">%s &middot; Powered by HandyFlow Creative Studio</p>
                </div>
              </div>
            </body></html>
            """.formatted(tenantName, proof.getVersionNumber(), msg,
                approvalUrl, approvalUrl, approvalUrl, tenantName);
    }

    /**
     * FIX: backlog 1.1 — signature changed from taking a whole
     * CreProofApprover entity to taking (approverName, approverEmail,
     * stepOrder, mode) directly — that entity is no longer the live
     * approval-state record (see class Javadoc), an ApprovalStepResponse
     * is. Content and behaviour are otherwise byte-for-byte identical to
     * the original: same subject-line logic (SEQUENTIAL gets "it's your
     * turn" phrasing), same template.
     */
    private void sendApproverEmail(String tenantName, CreProof proof, String approverName, String approverEmail,
                                   int stepOrder, ApprovalRule.ApprovalMode mode, String approvalUrl,
                                   String customMessage) {
        String subject = mode == ApprovalRule.ApprovalMode.SEQUENTIAL
                ? tenantName + " — It's your turn to review a proof"
                : tenantName + " — Please review and approve your proof";
        String html = buildApproverEmail(tenantName, proof, approverName, stepOrder, mode, approvalUrl, customMessage);
        emailService.send(approverEmail, subject, html);
    }

    private String buildApproverEmail(String tenantName, CreProof proof, String approverName, int stepOrder,
                                      ApprovalRule.ApprovalMode mode, String approvalUrl, String customMessage) {
        String msg = customMessage != null && !customMessage.isBlank()
                ? "<p style=\"background:#FFFBEB;border-left:3px solid #D97706;padding:12px 16px;border-radius:0 8px 8px 0;\">"
                + customMessage + "</p>" : "";
        String chainNote = mode == ApprovalRule.ApprovalMode.SEQUENTIAL
                ? "<p style=\"color:#64748B;font-size:13px;\">You are approver " + stepOrder
                + " in this review chain — the proof is ready for you now.</p>"
                : "<p style=\"color:#64748B;font-size:13px;\">This proof needs sign-off from multiple reviewers; "
                + "your review can happen independently of the others.</p>";
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"></head>
            <body style="font-family:Arial,sans-serif;background:#F1F5F9;margin:0;padding:0;">
              <div style="max-width:560px;margin:40px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
                <div style="background:#1B3A6B;padding:28px 32px;">
                  <h1 style="color:#fff;margin:0;font-size:20px">%s</h1>
                  <p style="color:rgba(255,255,255,0.7);margin:4px 0 0;font-size:13px">Creative Studio — Proof Review</p>
                </div>
                <div style="padding:32px;">
                  <p style="color:#374151;font-size:14px;line-height:1.6">Hi %s,</p>
                  <p style="color:#374151;font-size:14px;line-height:1.6">
                    A proof (Version %d) is ready for your review. Please click the button below to view it and share your feedback.
                  </p>
                  %s
                  %s
                  <p style="text-align:center;margin:28px 0;">
                    <a href="%s" style="background:#1B3A6B;color:#fff;padding:14px 32px;border-radius:8px;text-decoration:none;font-weight:700;font-size:16px;display:inline-block;">
                      Review &amp; Approve Proof
                    </a>
                  </p>
                  <p style="color:#64748B;font-size:13px;">
                    You can also approve or request changes directly from this link.<br>
                    No HandyFlow account is required — the link opens directly in your browser.<br><br>
                    <strong>This link expires in 72 hours.</strong>
                  </p>
                </div>
                <div style="background:#F8FAFC;padding:20px 32px;border-top:1px solid #E2E8F0;">
                  <p style="color:#94A3B8;font-size:12px;margin:0;">%s &middot; Powered by HandyFlow Creative Studio</p>
                </div>
              </div>
            </body></html>
            """.formatted(tenantName, approverName, proof.getVersionNumber(),
                chainNote, msg, approvalUrl, tenantName);
    }

    private JobResponse toJobResponse(CreJob j) {
        int proofCount       = proofRepo.findByJobIdOrderByVersionNumberDesc(j.getId()).size();
        int deliverableCount = deliverableRepo.findByJobIdOrderByCreatedAtDesc(j.getId()).size();
        return toJobResponseCounts(j, proofCount, deliverableCount);
    }

    private JobResponse toJobResponseCounts(CreJob j, int proofCount, int deliverableCount) {
        return new JobResponse(
                j.getId(), j.getCustomerId(), j.getClientName(), j.getClientEmail(),
                j.getTitle(), j.getJobType(), j.getDescription(), j.getBrief(),
                j.getStatus(), j.getPriority(), j.getDueDate(),
                j.getBudget(), j.getQuotedAmount(), j.getInvoiceId(),
                j.getNotes(), j.getAssignedTo(),
                proofCount, deliverableCount,
                j.getCreatedAt(), j.getUpdatedAt());
    }

    private ProofResponse toProofResponse(CreProof p, boolean includeComments) {
        List<CommentResponse> comments = includeComments
                ? commentRepo.findByProofIdOrderByCreatedAtAsc(p.getId())
                .stream().map(this::toCommentResponse).toList()
                : List.of();
        List<ApproverResponse> approvers = approverRepo.findByProofIdOrderByApprovalOrderAsc(p.getId())
                .stream().map(this::toApproverResponse).toList();
        return new ProofResponse(
                p.getId(), p.getJobId(), p.getVersionNumber(), p.getTitle(),
                p.getFileName(), p.getFileType(),
                p.getFileUrl() != null, p.getThumbnailUrl() != null,
                p.getStatus(), p.getApprovalToken(), p.getTokenExpiresAt(),
                p.getSentAt(), p.getSentToEmail(), p.getViewedAt(),
                p.getApprovedAt(), p.getApprovedByName(),
                p.getRejectionReason(), p.getNotes(),
                comments, p.getApprovalMode(), approvers, p.getCreatedAt());
    }

    private ApproverResponse toApproverResponse(CreProofApprover a) {
        return new ApproverResponse(a.getId(), a.getApproverName(), a.getApproverEmail(),
                a.getApprovalOrder(), a.getStatus(), a.getSentAt(), a.getApprovedAt(), a.getRejectionReason());
    }

    private CommentResponse toCommentResponse(CreProofComment c) {
        return new CommentResponse(c.getId(), c.getAuthorName(),
                c.getAuthorType(), c.getComment(), c.getTimecodeSeconds(),
                c.getAnchorX(), c.getAnchorY(), c.getCreatedAt());
    }

    private DeliverableResponse toDeliverableResponse(CreDeliverable d) {
        return new DeliverableResponse(d.getId(), d.getFileName(),
                d.getFileType(), d.getFileSize(), d.getNotes(),
                d.getUploadedBy(), d.getCreatedAt());
    }
}