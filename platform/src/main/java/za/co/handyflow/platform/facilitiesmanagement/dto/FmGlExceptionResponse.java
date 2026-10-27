package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A document that should be in the ledger but isn't. {@code type} is INVOICE or PAYMENT. */
public record FmGlExceptionResponse(
        String type, UUID id, UUID invoiceId, String reference, BigDecimal amount,
        int attempts, String lastError, Instant lastAttemptAt
) {}