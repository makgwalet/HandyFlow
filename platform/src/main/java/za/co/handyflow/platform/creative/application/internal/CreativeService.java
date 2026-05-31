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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeService {

    private final CreJobRepository          jobRepo;
    private final CreProofRepository        proofRepo;
    private final CreProofCommentRepository commentRepo;
    private final CreDeliverableRepository  deliverableRepo;
    private final EmailService              emailService;
    private final JdbcTemplate             jdbc;

    // ── Jobs ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<JobResponse> getJobs(TenantId tenantId, String status, Pageable pageable) {
        return jobRepo.findAll(tenantId, status, pageable).map(j -> toJobResponse(j));
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
            case "START"    -> job.startWork();
            case "SEND"     -> job.sendForApproval();
            case "REVISE"   -> job.requestRevision();
            case "APPROVE"  -> job.markApproved();
            case "DELIVER"  -> job.markDelivered();
            case "CANCEL"   -> job.cancel();
            default -> throw new HandyFlowException(
                    "Unknown action: " + action, HttpStatus.BAD_REQUEST, "INVALID_ACTION");
        }
        jobRepo.save(job);
        return toJobResponse(job);
    }

    @Transactional
    public void deleteJob(TenantId tenantId, UUID id) {
        CreJob job = findJob(tenantId, id);
        // soft delete via JDBC
        jdbc.update("UPDATE cre_jobs SET deleted_at = NOW() WHERE id = ?", id);
        log.info("Soft-deleted creative job={}", id);
    }

    // ── Proofs ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProofResponse> getProofs(TenantId tenantId, UUID jobId) {
        findJob(tenantId, jobId); // verify ownership
        return proofRepo.findByJobIdOrderByVersionNumberDesc(jobId)
                .stream().map(p -> toProofResponse(p, true)).toList();
    }

    @Transactional
    public ProofResponse uploadProof(TenantId tenantId, UUID jobId,
                                      UploadProofRequest req, UUID uploadedBy) {
        CreJob job = findJob(tenantId, jobId);

        // Supersede all existing PENDING proofs for this job
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

        // Auto-advance job status to AWAITING_APPROVAL
        if (!"AWAITING_APPROVAL".equals(job.getStatus())) {
            job.sendForApproval();
            jobRepo.save(job);
        }

        log.info("Uploaded proof v{} for job={}", nextVersion, jobId);
        return toProofResponse(proof, true);
    }

    @Transactional
    public ProofResponse sendProofToClient(TenantId tenantId, UUID jobId,
                                            UUID proofId, SendProofRequest req) {
        findJob(tenantId, jobId); // verify ownership
        CreProof proof = proofRepo.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Proof", proofId.toString()));

        if (!proof.isPending()) {
            throw new HandyFlowException(
                    "Only PENDING proofs can be sent for approval",
                    HttpStatus.BAD_REQUEST, "PROOF_NOT_PENDING");
        }

        // Fetch tenant name for the email
        String tenantName = fetchTenantName(tenantId);
        String approvalUrl = "https://app.handyflow.co.za/creative/approve/"
                + proof.getApprovalToken();

        String subject = tenantName + " — Please review and approve your proof";
        String html = buildApprovalEmail(tenantName, proof, approvalUrl, req.message());

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

    @Transactional
    public ProofResponse addTeamComment(TenantId tenantId, UUID jobId,
                                         UUID proofId, AddCommentRequest req,
                                         String teamMemberName) {
        findJob(tenantId, jobId);
        CreProof proof = proofRepo.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Proof", proofId.toString()));

        CreProofComment comment = CreProofComment.create(
                proofId, tenantId.getValue(), teamMemberName, "TEAM", req.comment());
        commentRepo.save(comment);
        return toProofResponse(proof, true);
    }

    // ── Public proof approval (no auth) ──────────────────────────────────────

    @Transactional(readOnly = true)
    public PublicProofResponse getProofByToken(String token) {
        CreProof proof = findByToken(token);
        CreJob job = jobRepo.findById(proof.getJobId())
                .orElseThrow(() -> new HandyFlowException(
                        "Job not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        String tenantName = fetchTenantName(job.getTenantId());
        List<CommentResponse> comments = commentRepo
                .findByProofIdOrderByCreatedAtAsc(proof.getId())
                .stream().map(this::toCommentResponse).toList();
        return new PublicProofResponse(
                proof.getId(), job.getTitle(), job.getClientName(), tenantName,
                proof.getVersionNumber(), proof.getTitle(),
                proof.getFileUrl(), proof.getThumbnailUrl(),
                proof.getFileName(), proof.getFileType(),
                proof.getStatus(), comments, proof.getCreatedAt());
    }

    @Transactional
    public void approveProofByToken(String token, ApproveProofRequest req, String clientIp) {
        CreProof proof = findByToken(token);
        proof.approve(req.clientName(), req.clientEmail(), clientIp);
        proofRepo.save(proof);

        // Update job status to APPROVED
        jobRepo.findById(proof.getJobId()).ifPresent(job -> {
            job.markApproved();
            jobRepo.save(job);
        });

        log.info("Proof={} approved by client={} ip={}", proof.getId(), req.clientName(), clientIp);
    }

    @Transactional
    public void rejectProofByToken(String token, RejectProofRequest req) {
        CreProof proof = findByToken(token);
        proof.reject(req.reason());
        proofRepo.save(proof);

        // Record client rejection as a comment
        CreProofComment comment = CreProofComment.create(
                proof.getId(), proof.getTenantId(),  // UUID directly
                "Client", "CLIENT", "Rejected: " + req.reason());
        commentRepo.save(comment);

        // Move job back to IN_REVISION
        jobRepo.findById(proof.getJobId()).ifPresent(job -> {
            job.requestRevision();
            jobRepo.save(job);
        });

        log.info("Proof={} rejected: {}", proof.getId(), req.reason());
    }

    @Transactional
    public void addClientComment(String token, AddCommentRequest req) {
        CreProof proof = findByToken(token);
        String authorName = req.authorName() != null ? req.authorName() : "Client";
        CreProofComment comment = CreProofComment.create(
                proof.getId(), proof.getTenantId(),  // UUID directly
                authorName, "CLIENT", req.comment());
        commentRepo.save(comment);
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

        // Auto-advance to DELIVERED if job is APPROVED
        if ("APPROVED".equals(job.getStatus())) {
            job.markDelivered();
            jobRepo.save(job);
        }
        return toDeliverableResponse(d);
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
                countOverdue(tenantId)
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CreJob findJob(TenantId tenantId, UUID id) {
        return jobRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", id.toString()));
    }

    private CreProof findByToken(String token) {
        CreProof proof = proofRepo.findByApprovalToken(token)
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid or expired approval link",
                        HttpStatus.BAD_REQUEST, "INVALID_TOKEN"));
        if (!proof.isTokenValid()) {
            throw new HandyFlowException(
                    proof.isApproved() ? "This proof has already been approved."
                    : "This approval link has expired. Ask your designer to resend it.",
                    HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
        }
        return proof;
    }

    private long countOverdue(TenantId tenantId) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM cre_jobs
                    WHERE tenant_id = ? AND deleted_at IS NULL
                    AND status NOT IN ('APPROVED','DELIVERED','INVOICED','CANCELLED')
                    AND due_date < CURRENT_DATE
                    """,
                    Long.class, tenantId.getValue());
        } catch (Exception e) { return 0; }
    }

    private String fetchTenantName(TenantId tenantId) {
        try {
            return jdbc.queryForObject(
                    "SELECT name FROM tenants WHERE id = ?",
                    String.class, tenantId.getValue());
        } catch (Exception e) { return "HandyFlow"; }
    }

    private String buildApprovalEmail(String tenantName, CreProof proof,
                                       String approvalUrl, String customMessage) {
        String msg = customMessage != null && !customMessage.isBlank()
                ? "<p>" + customMessage + "</p>" : "";
        return """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto">
              <h2 style="color:#1B3A6B">%s — Proof Ready for Review</h2>
              <p>Hi,</p>
              <p>Your proof (version %d) is ready for review.</p>
              %s
              <p>Please click the button below to view the proof and share your feedback:</p>
              <p>
                <a href="%s" style="background:#1B3A6B;color:white;padding:14px 28px;
                   border-radius:8px;text-decoration:none;font-weight:bold;
                   display:inline-block;font-size:16px">
                  View &amp; Approve Proof
                </a>
              </p>
              <p style="color:#64748B;font-size:13px">
                This link expires in 72 hours.<br>
                You do not need to create an account to approve this proof.
              </p>
            </div>
            """.formatted(tenantName, proof.getVersionNumber(), msg, approvalUrl);
    }

    private JobResponse toJobResponse(CreJob j) {
        int proofCount = proofRepo.findByJobIdOrderByVersionNumberDesc(j.getId()).size();
        int deliverableCount = deliverableRepo.findByJobIdOrderByCreatedAtDesc(j.getId()).size();
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
        return new ProofResponse(
                p.getId(), p.getJobId(), p.getVersionNumber(), p.getTitle(),
                p.getFileName(), p.getFileType(),
                p.getFileUrl() != null, p.getThumbnailUrl() != null,
                p.getStatus(), p.getApprovalToken(), p.getTokenExpiresAt(),
                p.getSentAt(), p.getSentToEmail(),
                p.getApprovedAt(), p.getApprovedByName(),
                p.getRejectionReason(), p.getNotes(),
                comments, p.getCreatedAt());
    }

    private CommentResponse toCommentResponse(CreProofComment c) {
        return new CommentResponse(c.getId(), c.getAuthorName(),
                c.getAuthorType(), c.getComment(), c.getCreatedAt());
    }

    private DeliverableResponse toDeliverableResponse(CreDeliverable d) {
        return new DeliverableResponse(d.getId(), d.getFileName(),
                d.getFileType(), d.getFileSize(), d.getNotes(),
                d.getUploadedBy(), d.getCreatedAt());
    }
}
