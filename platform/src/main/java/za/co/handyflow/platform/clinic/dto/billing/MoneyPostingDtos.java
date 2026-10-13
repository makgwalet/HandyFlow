package za.co.handyflow.platform.clinic.dto.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Claim money that has not reached the general ledger yet, and the action that sends it there (patch 0185). */
public final class MoneyPostingDtos {
    private MoneyPostingDtos() {}

    /** @param type SCHEME_PAYMENT_RECEIVED or CLAIM_WRITTEN_OFF; @param status PENDING or FAILED; @param needsBank true for a scheme payment */
    public record PostingView(UUID id, UUID claimId, String patientName, String type, BigDecimal amount, String reference,
                              LocalDate occurredOn, String status, String reason, boolean needsBank) {}

    public record BankChoice(UUID id, String label) {}

    /** {@code bankAccountId} is needed when any chosen posting is a scheme payment. Leave {@code postingIds} empty to post everything waiting. */
    public record PostRequest(UUID bankAccountId, List<UUID> postingIds) {}

    public record PostFailure(UUID id, String reason) {}

    public record PostResult(int posted, List<PostFailure> failed) {}
}
