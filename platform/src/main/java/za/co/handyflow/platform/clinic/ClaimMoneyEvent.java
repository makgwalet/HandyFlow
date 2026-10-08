package za.co.handyflow.platform.clinic;

import za.co.handyflow.platform.shared.DomainEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Money moved on a claim (CLINIC-DEC-007): the clinic says what happened, Accounting decides how it posts and which
 * accounts it uses. {@code type} is one of SCHEME_PAYMENT_RECEIVED, CLAIM_WRITTEN_OFF, CREDIT_NOTE_ISSUED, CLAIM_VOIDED.
 * {@code reference} is the scheme's payment reference, the credit note number, or null.
 */
public record ClaimMoneyEvent(
        TenantId tenantId,
        String type,
        UUID claimId,
        UUID patientId,
        BigDecimal amount,
        String reference,
        UUID byUserId,
        Instant occurredOn
) implements DomainEvent {

    public static final String SCHEME_PAYMENT_RECEIVED = "SCHEME_PAYMENT_RECEIVED";
    public static final String CLAIM_WRITTEN_OFF = "CLAIM_WRITTEN_OFF";
    public static final String CREDIT_NOTE_ISSUED = "CREDIT_NOTE_ISSUED";
    public static final String CLAIM_VOIDED = "CLAIM_VOIDED";

    public static ClaimMoneyEvent of(TenantId tenantId, String type, UUID claimId, UUID patientId,
                                     BigDecimal amount, String reference, UUID byUserId) {
        return new ClaimMoneyEvent(tenantId, type, claimId, patientId, amount, reference, byUserId, Instant.now());
    }
}
