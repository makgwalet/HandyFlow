package za.co.handyflow.platform.supplychain.dto;

import java.time.Instant;
import java.util.UUID;

/** Account numbers are always masked in responses. */
public record BankChangeResponse(
        UUID id,
        UUID supplierId,
        String status,
        String proposedBankName,
        String proposedAccountMasked,
        String proposedBranchCode,
        String previousBankName,
        String previousAccountMasked,
        String reason,
        String warnings,
        String requestedByName,
        Instant createdAt,
        String verifiedByName,
        Instant verifiedAt,
        String verificationMethod,
        String verificationNote
) {}
