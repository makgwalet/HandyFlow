package za.co.handyflow.platform.clinic.dto.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for the claim money ledger (write-off, credit note, void, scheme payment allocation). */
public final class ClaimMoneyDtos {
    private ClaimMoneyDtos() {}

    public record AdjustmentRequest(BigDecimal amount, String reason) {}
    public record VoidRequest(String reason) {}

    /** One line of a scheme payment split. */
    public record ShareRequest(UUID claimId, BigDecimal amount) {}

    /**
     * A scheme payment to spread over claims. Leave {@code shares} empty for oldest-first; give them (and an
     * {@code overrideReason}) to choose the split yourself. {@code preview=true} shows the split and records nothing.
     */
    public record AllocateRequest(String schemeName, BigDecimal amount, LocalDate receivedOn, String reference,
                                  List<ShareRequest> shares, String overrideReason, boolean preview) {}

    public record ShareResponse(UUID claimId, String patientName, String claimReference, BigDecimal outstandingBefore,
                                BigDecimal amount, BigDecimal outstandingAfter) {}

    public record AllocationResponse(boolean recorded, String method, UUID batchId, BigDecimal amount, List<ShareResponse> shares) {}

    public record LedgerEntry(String kind, BigDecimal amount, LocalDate date, String reference, String note,
                              UUID byUserId, Instant recordedAt) {}

    public record Ledger(UUID claimId, String status, BigDecimal schemeDue, BigDecimal paid, BigDecimal writtenOff,
                         BigDecimal credited, BigDecimal outstanding, List<LedgerEntry> entries) {}
}
