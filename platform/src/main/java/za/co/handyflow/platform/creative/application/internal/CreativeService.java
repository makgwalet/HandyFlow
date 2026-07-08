package za.co.handyflow.platform.creative.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.creative.domain.model.*;
import za.co.handyflow.platform.creative.domain.repository.*;
import za.co.handyflow.platform.creative.dto.*;
import za.co.handyflow.platform.shared.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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

        if ("SINGLE".equals(proof.getApprovalMode())) {
            // Unchanged existing behaviour — single approver, single token
            // on the proof itself.
            String tenantName  = fetchTenantName(tenantId);
            String approvalUrl = "https://app.handyflow.co.za/creative/approve/" + proof.getApprovalToken();
            String subject     = tenantName + " — Please review and approve your proof";
            String html        = buildApprovalEmail(tenantName, proof, approvalUrl, req.message());
            try {
                emailService.send(req.email(), subject, html);
                proof.markSent(req.email());
                proofRepo.save(proof);
                log.info("Sent proof={} to {}", proofId, req.email());
            } catch (Exception e) {
                log.error("Failed to send proof email: {}", e.getMessage());
                throw new HandyFlowException(
                        "Failed to send email: " + e.getMessage(),
                        HttpStatus.INTERNAL_SERVER_ERROR, "EMAIL_FAILED");
            }
            return toProofResponse(proof, true);
        }

        // Multi-stakeholder — SEQUENTIAL or PARALLEL
        List<CreProofApprover> approvers = approverRepo.findByProofIdOrderByApprovalOrderAsc(proofId);
        if (approvers.isEmpty()) {
            throw new HandyFlowException(
                    "This proof is set to " + proof.getApprovalMode() + " approval but has no approvers " +
                            "configured — add approvers before sending.",
                    HttpStatus.BAD_REQUEST, "NO_APPROVERS_CONFIGURED");
        }

        // SEQUENTIAL: only notify the first approver — the rest are
        // notified one at a time as each prior approver signs off, in
        // approveProofByToken(). PARALLEL: notify everyone at once.
        List<CreProofApprover> toNotify = "PARALLEL".equals(proof.getApprovalMode())
                ? approvers
                : approvers.subList(0, 1);

        String tenantName = fetchTenantName(tenantId);
        int sent = 0;
        for (CreProofApprover approver : toNotify) {
            try {
                sendApproverEmail(tenantName, proof, approver, req.message());
                approver.markSent();
                approverRepo.save(approver);
                sent++;
            } catch (Exception e) {
                // FIX: one failed email must not block the others (PARALLEL
                // mode sends to several people at once) or corrupt state —
                // this approver's sentAt simply stays null, visibly showing
                // it wasn't actually delivered, rather than the whole send
                // failing or falsely claiming success for everyone.
                log.error("Failed to send approval email to approver={} for proof={}: {}",
                        approver.getApproverEmail(), proofId, e.getMessage(), e);
            }
        }

        if (sent == 0) {
            throw new HandyFlowException(
                    "Failed to send to any approver — check the configured email addresses.",
                    HttpStatus.INTERNAL_SERVER_ERROR, "EMAIL_FAILED");
        }

        String allEmails = approvers.stream().map(CreProofApprover::getApproverEmail)
                .collect(Collectors.joining(", "));
        proof.markSent(allEmails);
        proofRepo.save(proof);

        log.info("Sent proof={} for {} approval — {} of {} approver(s) notified",
                proofId, proof.getApprovalMode(), sent, toNotify.size());

        return toProofResponse(proof, true);
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
        CreProofApprover approver = res.approver();

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

        // NEW: multi-stakeholder context — who am I, and where does
        // everyone else in the chain stand. Empty/null for SINGLE mode.
        String myApproverName = approver != null ? approver.getApproverName() : null;
        List<PublicProofResponse.ApproverSummary> others = approver != null
                ? approverRepo.findByProofIdOrderByApprovalOrderAsc(proof.getId()).stream()
                .map(a -> new PublicProofResponse.ApproverSummary(
                        a.getApproverName(), a.getApprovalOrder(), a.getStatus()))
                .toList()
                : List.of();

        return new PublicProofResponse(
                proof.getId(), job.getTitle(), job.getClientName(), tenantName,
                proof.getVersionNumber(), proof.getTitle(),
                proof.getFileUrl(), proof.getThumbnailUrl(),
                proof.getFileName(), proof.getFileType(),
                proof.getStatus(), comments, proof.getCreatedAt(),
                proof.getApprovalMode(), myApproverName, others);
    }

    @Transactional
    public void approveProofByToken(String token, ApproveProofRequest req, String clientIp) {
        TokenResolution res = resolveToken(token);
        CreProof proof = res.proof();
        CreProofApprover approver = res.approver();

        if (approver == null) {
            // SINGLE mode — unchanged existing behaviour.
            proof.approve(req.clientName(), req.clientEmail(), clientIp);
            proofRepo.save(proof);
            completeApproval(proof);
            log.info("Proof={} approved by client={} ip={}", proof.getId(), req.clientName(), clientIp);
            return;
        }

        // Multi-stakeholder
        approver.approve(clientIp);
        approverRepo.save(approver);
        log.info("Proof={} approver={} approved ip={}", proof.getId(), approver.getApproverName(), clientIp);

        List<CreProofApprover> all = approverRepo.findByProofIdOrderByApprovalOrderAsc(proof.getId());
        boolean allApproved = all.stream().allMatch(CreProofApprover::isApproved);

        if (allApproved) {
            // Whole proof is now approved — record the completing approver
            // on the proof's own single-approver-shaped fields too, since
            // other parts of the system (the approval certificate PDF,
            // ProofResponse.approvedByName) still read those directly
            // rather than the approver list.
            proof.approve(approver.getApproverName(), approver.getApproverEmail(), clientIp);
            proofRepo.save(proof);
            completeApproval(proof);
        } else if ("SEQUENTIAL".equals(proof.getApprovalMode())) {
            all.stream()
                    .filter(a -> a.getApprovalOrder() > approver.getApprovalOrder() && a.isPending())
                    .min(Comparator.comparingInt(CreProofApprover::getApprovalOrder))
                    .ifPresent(next -> jobRepo.findById(proof.getJobId())
                            .ifPresent(job -> notifyNextApprover(job, proof, next)));
        }
        // PARALLEL, not all approved yet: nothing further to do — the
        // others were already notified when the proof was sent, and are
        // just waiting on their own review.
    }

    private void completeApproval(CreProof proof) {
        jobRepo.findById(proof.getJobId()).ifPresent(job -> {
            job.markApproved();
            jobRepo.save(job);
            log.info("Proof={} fully approved", proof.getId());
        });
    }

    // FIX: takes the already-loaded CreJob rather than trying to construct
    // a TenantId from proof.getTenantId() (a raw UUID) — CreProof stores
    // tenantId as a plain UUID, not the TenantId value object CreJob uses,
    // and there's no confirmed TenantId.of(UUID) factory available to
    // safely bridge the two. job.getTenantId() is already the right type,
    // proven by every other fetchTenantName() call in this class.
    private void notifyNextApprover(CreJob job, CreProof proof, CreProofApprover next) {
        next.markSent();
        approverRepo.save(next);
        try {
            String tenantName = fetchTenantName(job.getTenantId());
            sendApproverEmail(tenantName, proof, next, null);
        } catch (Exception e) {
            log.error("Failed to notify next approver={} for proof={}: {}",
                    next.getApproverEmail(), proof.getId(), e.getMessage(), e);
        }
    }

    @Transactional
    public void rejectProofByToken(String token, RejectProofRequest req) {
        TokenResolution res = resolveToken(token);
        CreProof proof = res.proof();
        CreProofApprover approver = res.approver();

        String rejectorName = approver != null ? approver.getApproverName() : "Client";

        if (approver != null) {
            approver.reject(req.reason());
            approverRepo.save(approver);
            // DELIBERATE DESIGN CHOICE: any single approver rejecting stops
            // the whole chain immediately — remaining approvers (sequential:
            // not yet notified; parallel: still pending) are never asked to
            // review a proof that's already going back for changes. Matches
            // how a real internal review chain works: one "no" from any
            // required reviewer sends it back; it doesn't poll everyone
            // else first.
        }

        proof.reject(req.reason());
        proofRepo.save(proof);

        commentRepo.save(CreProofComment.create(
                proof.getId(), proof.getTenantId(),
                rejectorName, "CLIENT", "Changes requested: " + req.reason(), null, null, null));

        jobRepo.findById(proof.getJobId()).ifPresent(job -> {
            job.requestRevision();
            jobRepo.save(job);

            // FIX: the public controller's success message for this action
            // literally says "Your designer has been notified" — but nothing
            // here ever notified anyone.
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
    // NEW: previously the staff-facing UI had no way to actually view a
    // proof's file at all — ProofResponse deliberately excludes fileUrl
    // (reasonable, avoids putting a base64 blob in every list response), but
    // that meant there was no dedicated way to fetch it on demand either.
    // Staff had to copy the public approval link and open it themselves to
    // see what a proof actually looked like. This is also the prerequisite
    // for side-by-side version comparison — you can't compare two versions
    // you can't see.

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

    /** Carries whichever token actually matched — approver is null for SINGLE-mode proofs. */
    private record TokenResolution(CreProof proof, CreProofApprover approver) {}

    private TokenResolution resolveToken(String token) {
        // Try the proof's own token first — this is the entire lookup for
        // SINGLE mode, and stays completely unchanged from before.
        Optional<CreProof> singleProof = proofRepo.findByApprovalToken(token);
        if (singleProof.isPresent()) {
            CreProof proof = singleProof.get();
            if (!proof.isTokenValid()) {
                throw new HandyFlowException(
                        proof.isApproved()
                                ? "This proof has already been approved."
                                : "This approval link has expired. Ask your designer to resend it.",
                        HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
            }
            return new TokenResolution(proof, null);
        }

        // Not a proof-level token — try an approver-level one.
        CreProofApprover approver = approverRepo.findByApprovalToken(token)
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid or expired approval link", HttpStatus.BAD_REQUEST, "INVALID_TOKEN"));
        CreProof proof = proofRepo.findById(approver.getProofId())
                .orElseThrow(() -> new HandyFlowException("Proof not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (approver.isRejected() || proof.isRejected()) {
            throw new HandyFlowException(
                    "This proof has been rejected — no further action is needed.",
                    HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
        }
        if (approver.isApproved()) {
            throw new HandyFlowException(
                    "You have already approved this proof.", HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
        }
        if (!approver.isTokenValid()) {
            throw new HandyFlowException(
                    "This approval link has expired.", HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
        }

        // FIX: enforces sequential ordering at the system level, not just
        // by relying on "we only ever emailed the current approver". A
        // later approver's token is genuinely valid and unused — if it
        // were guessed or reused before it's actually their turn, this
        // stops the chain being approved out of order rather than trusting
        // "nobody would have it yet" as the only protection.
        if ("SEQUENTIAL".equals(proof.getApprovalMode())) {
            List<CreProofApprover> all = approverRepo.findByProofIdOrderByApprovalOrderAsc(proof.getId());
            boolean earlierStillPending = all.stream()
                    .anyMatch(a -> a.getApprovalOrder() < approver.getApprovalOrder() && a.isPending());
            if (earlierStillPending) {
                throw new HandyFlowException(
                        "It's not your turn yet — an earlier approver still needs to review this proof first.",
                        HttpStatus.BAD_REQUEST, "NOT_YOUR_TURN");
            }
        }

        return new TokenResolution(proof, approver);
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
        try {
            String tenantName  = fetchTenantName(job.getTenantId());
            String approvalUrl = "https://app.handyflow.co.za/creative/approve/" + proof.getApprovalToken();
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

    // NEW: the multi-stakeholder equivalent of the block above — addressed
    // to a named approver rather than a generic "Hi,", and mentions their
    // position in the chain for SEQUENTIAL proofs so it's clear this is
    // specifically their turn, not a general notification.
    private void sendApproverEmail(String tenantName, CreProof proof,
                                   CreProofApprover approver, String customMessage) {
        String approvalUrl = "https://app.handyflow.co.za/creative/approve/" + approver.getApprovalToken();
        String subject = "SEQUENTIAL".equals(proof.getApprovalMode())
                ? tenantName + " — It's your turn to review a proof"
                : tenantName + " — Please review and approve your proof";
        String html = buildApproverEmail(tenantName, proof, approver, approvalUrl, customMessage);
        emailService.send(approver.getApproverEmail(), subject, html);
    }

    private String buildApproverEmail(String tenantName, CreProof proof, CreProofApprover approver,
                                      String approvalUrl, String customMessage) {
        String msg = customMessage != null && !customMessage.isBlank()
                ? "<p style=\"background:#FFFBEB;border-left:3px solid #D97706;padding:12px 16px;border-radius:0 8px 8px 0;\">"
                + customMessage + "</p>" : "";
        String chainNote = "SEQUENTIAL".equals(proof.getApprovalMode())
                ? "<p style=\"color:#64748B;font-size:13px;\">You are approver " + approver.getApprovalOrder()
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
            """.formatted(tenantName, approver.getApproverName(), proof.getVersionNumber(),
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
