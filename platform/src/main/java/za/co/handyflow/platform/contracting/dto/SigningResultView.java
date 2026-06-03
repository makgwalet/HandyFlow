package za.co.handyflow.platform.contracting.dto;

import java.time.Instant;
import java.util.UUID;

// Result returned after successful signing
public record SigningResultView(
        UUID contractId,
        String contractNumber,
        String title,
        boolean fullyExecuted,   // true if all parties have now signed
        String partyName,
        Instant signedAt,
        String message           // human-readable confirmation
) {}
