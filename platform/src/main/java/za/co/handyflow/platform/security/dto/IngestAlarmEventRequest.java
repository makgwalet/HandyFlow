package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

public record IngestAlarmEventRequest(
        UUID    siteId,                  // nullable — some sources aren't site-scoped
        @NotBlank String source,         // ALARM_PANEL | PANIC_BUTTON | CCTV_MOTION | DRONE | DURESS | MANUAL | OTHER
        String  rawPayload,              // verbatim webhook body, stored as JSONB
        String  severity,                // LOW | MEDIUM | HIGH | CRITICAL — defaults to MEDIUM if null
        UUID    triggeredByGuardId,      // nullable
        BigDecimal latitude,
        BigDecimal longitude,
        String  description
) {}
