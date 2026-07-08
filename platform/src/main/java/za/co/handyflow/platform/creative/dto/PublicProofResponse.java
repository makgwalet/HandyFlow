package za.co.handyflow.platform.creative.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * NOTE: this file was not directly available when this change was made —
 * reconstructed from its exact observed constructor call in
 * CreativeService.getProofByToken(). The original 13 fields and their
 * order are taken directly from that call site and should be reliable; the
 * three new fields are appended at the end specifically so they can't
 * shift the position of anything that already existed. Worth a quick
 * diff against your actual source if you still have it, just to confirm
 * nothing else in this file (annotations, etc.) got lost in the
 * reconstruction.
 */
public record PublicProofResponse(
        UUID    proofId,
        String  jobTitle,
        String  clientName,
        String  tenantName,
        int     versionNumber,
        String  title,
        String  fileUrl,
        String  thumbnailUrl,
        String  fileName,
        String  fileType,
        String  status,
        List<CommentResponse> comments,
        Instant createdAt,

        // NEW: multi-stakeholder approval context. All null/empty for
        // SINGLE-mode proofs (the existing, unchanged behaviour) — a
        // client viewing a legacy single-approver proof sees exactly what
        // they always did.
        String approvalMode,                 // SINGLE | SEQUENTIAL | PARALLEL
        String myApproverName,               // null for SINGLE mode
        List<PublicProofResponse.ApproverSummary> otherApprovers  // empty for SINGLE mode
) {
    /** Read-only visibility into the approval chain — no tokens, nothing private. */
    public record ApproverSummary(String approverName, int approvalOrder, String status) {}
}
