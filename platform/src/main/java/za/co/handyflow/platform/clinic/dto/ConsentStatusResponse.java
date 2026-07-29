package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;

public record ConsentStatusResponse(
        String consentType,
        String status,        // GRANTED | REVOKED | NOT_RECORDED
        Instant lastActionAt,
        String method,
        String notes
) {}