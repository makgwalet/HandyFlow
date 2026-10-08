package za.co.handyflow.platform.clinic.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A patient's account: what each visit cost, what the scheme and the patient have paid, and what is owing (patch 0161). */
public final class AccountDtos {
    private AccountDtos() {}

    /** One visit on the account. {@code claimStatus} is NOT_BILLED when no claim exists yet. */
    public record AccountVisit(
            UUID consultationId, UUID claimId, Instant visitDate, String chiefComplaint,
            String claimStatus, String schemeName,
            BigDecimal charged, BigDecimal schemePortion, BigDecimal patientPortion,
            BigDecimal schemeOutstanding, BigDecimal paidAgainstVisit) {}

    public record AccountPayment(UUID id, UUID claimId, Instant paidAt, String method, BigDecimal amount, String reference) {}

    public record AccountResponse(
            UUID patientId,
            BigDecimal totalCharged, BigDecimal schemeShare, BigDecimal patientShare,
            BigDecimal paidByPatient, BigDecimal balanceOwing, BigDecimal schemeOutstanding,
            int visitsNotBilled,
            List<AccountVisit> visits, List<AccountPayment> payments) {}
}
