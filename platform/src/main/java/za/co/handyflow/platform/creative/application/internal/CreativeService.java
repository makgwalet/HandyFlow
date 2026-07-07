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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeService {

    private final CreJobRepository          jobRepo;
    private final CreProofRepository        proofRepo;
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

    @Transactional
    public ProofResponse addTeamComment(TenantId tenantId, UUID jobId,
                                        UUID proofId, AddCommentRequest req,
                                        String teamMemberName) {
        findJob(tenantId, jobId);
        CreProof proof = proofRepo.findById(proofId)
                .orElseThrow(() -> new ResourceNotFoundException("Proof", proofId.toString()));
        commentRepo.save(CreProofComment.create(
                proofId, tenantId.getValue(), teamMemberName, "TEAM", req.comment()));
        return toProofResponse(proof, true);
    }

    // ── Public proof approval (no auth) ──────────────────────────────────────

    @Transactional(readOnly = true)
    public PublicProofResponse getProofByToken(String token) {
        CreProof proof = findByToken(token);
        CreJob job = jobRepo.findById(proof.getJobId())
                .orElseThrow(() -> new HandyFlowException("Job not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        String tenantName = fetchTenantName(job.getTenantId());
        List<CommentResponse> comments = commentRepo.findByProofIdOrderByCreatedAtAsc(proof.getId())
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

        jobRepo.findById(proof.getJobId()).ifPresent(job -> {
            job.markApproved();
            jobRepo.save(job);
            log.info("Proof={} approved by client={} ip={}", proof.getId(), req.clientName(), clientIp);
        });
    }

    @Transactional
    public void rejectProofByToken(String token, RejectProofRequest req) {
        CreProof proof = findByToken(token);
        proof.reject(req.reason());
        proofRepo.save(proof);

        commentRepo.save(CreProofComment.create(
                proof.getId(), proof.getTenantId(),
                "Client", "CLIENT", "Changes requested: " + req.reason()));

        jobRepo.findById(proof.getJobId()).ifPresent(job -> {
            job.requestRevision();
            jobRepo.save(job);

            // FIX: the public controller's success message for this action
            // literally says "Your designer has been notified" — but nothing
            // here ever notified anyone. Notify whoever is actually
            // responsible for the job: the assigned designer if one is set,
            // falling back to whoever created it if not. Wrapped so a
            // notification failure can never affect the client-facing
            // rejection that already succeeded — same principle as every
            // other notification call site fixed this session.
            try {
                UUID recipientId = job.getAssignedTo() != null ? job.getAssignedTo() : job.getCreatedBy();
                String recipientEmail = fetchUserEmail(recipientId);
                if (recipientEmail != null) {
                    emailService.send(recipientEmail,
                            "Changes requested: " + job.getTitle(),
                            buildRejectionNotificationEmail(job.getTitle(), proof.getVersionNumber(), req.reason()));
                } else {
                    log.warn("Proof={} rejected but no notifiable email found for job={} " +
                                    "(assignedTo={}, createdBy={}) — nobody was actually notified",
                            proof.getId(), job.getId(), job.getAssignedTo(), job.getCreatedBy());
                }
            } catch (Exception e) {
                log.error("Failed to send rejection notification for proof={}: {}",
                        proof.getId(), e.getMessage(), e);
            }
        });

        log.info("Proof={} rejected: {}", proof.getId(), req.reason());
    }

    @Transactional
    public void addClientComment(String token, AddCommentRequest req) {
        CreProof proof = findByToken(token);
        String authorName = req.authorName() != null ? req.authorName() : "Client";
        commentRepo.save(CreProofComment.create(
                proof.getId(), proof.getTenantId(), authorName, "CLIENT", req.comment()));
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
                countOverdue(tenantId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CreJob findJob(TenantId tenantId, UUID id) {
        return jobRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", id.toString()));
    }

    private CreProof findByToken(String token) {
        CreProof proof = proofRepo.findByApprovalToken(token)
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid or expired approval link", HttpStatus.BAD_REQUEST, "INVALID_TOKEN"));
        if (!proof.isTokenValid()) {
            throw new HandyFlowException(
                    proof.isApproved()
                            ? "This proof has already been approved."
                            : "This approval link has expired. Ask your designer to resend it.",
                    HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED");
        }
        return proof;
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