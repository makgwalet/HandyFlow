package za.co.handyflow.platform.clinic.dto.billing;

import java.util.List;
import java.util.UUID;

/**
 * FIX: "batch claim submission" gap — ClaimsTab could only submit one claim
 * at a time (POST /claims/{id}/submit); practices with volume typically
 * want to submit a batch at once. Per-claim results are returned rather
 * than an all-or-nothing outcome, since one claim missing an ICD-10 code
 * shouldn't block the others in the batch from submitting.
 */
public record BatchSubmitClaimsResponse(
        int submitted,
        int failed,
        List<BatchSubmitResult> results
) {
    public record BatchSubmitResult(UUID claimId, boolean success, String message) {}
}