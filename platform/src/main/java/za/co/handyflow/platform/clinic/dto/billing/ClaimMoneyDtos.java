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
     * {@code overrideReason}) to choose the split yourself. Give {@code schemeNames} when one remittance covers several schemes. {@code preview=true} shows the split and records nothing.
     */
    public record AllocateRequest(String schemeName, BigDecimal amount, LocalDate receivedOn, String reference,
                                  List<ShareRequest> shares, String overrideReason, boolean preview, List<String> schemeNames) {
        public AllocateRequest(String schemeName, BigDecimal amount, LocalDate receivedOn, String reference,
                               List<ShareRequest> shares, String overrideReason, boolean preview) {
            this(schemeName, amount, receivedOn, reference, shares, overrideReason, preview, null);
        }

        /** The schemes this remittance covers: {@code schemeNames} when given (one remittance, several schemes), otherwise {@code schemeName}. Names compare ignoring case. */
        public List<String> schemes() {
            List<String> raw = schemeNames != null && !schemeNames.isEmpty() ? schemeNames : (schemeName == null ? List.<String>of() : List.of(schemeName));
            java.util.LinkedHashMap<String, String> byKey = new java.util.LinkedHashMap<>();
            for (String n : raw) if (n != null && !n.isBlank()) byKey.putIfAbsent(n.trim().toLowerCase(), n.trim());
            return List.copyOf(byKey.values());
        }
    }

    public record ShareResponse(UUID claimId, String patientName, String claimReference, BigDecimal outstandingBefore,
                                BigDecimal amount, BigDecimal outstandingAfter) {}

    public record AllocationResponse(boolean recorded, String method, UUID batchId, BigDecimal amount, List<ShareResponse> shares) {}

    /**
     * One movement on the claim. {@code id} is the payment's id for SCHEME_PAYMENT and PAYMENT_REVERSAL (the payment reversed);
     * {@code reversed} is true on a payment that has since been reversed.
     */
    public record LedgerEntry(String kind, BigDecimal amount, LocalDate date, String reference, String note,
                              UUID byUserId, Instant recordedAt, UUID id, boolean reversed) {
        public LedgerEntry(String kind, BigDecimal amount, LocalDate date, String reference, String note, UUID byUserId, Instant recordedAt) {
            this(kind, amount, date, reference, note, byUserId, recordedAt, null, false);
        }
    }

    public record ReversePaymentRequest(String reason) {}
    public record NotesRequest(String notes) {}
    /** Give {@code corrected} to change the scheme details or lines; leave it out to copy the claim as it stands. */
    public record CorrectClaimRequest(String reason, za.co.handyflow.platform.clinic.dto.billing.CreateClaimRequest corrected) {}

    public record Ledger(UUID claimId, String status, BigDecimal schemeDue, BigDecimal paid, BigDecimal writtenOff,
                         BigDecimal credited, BigDecimal outstanding, List<LedgerEntry> entries) {}
}
