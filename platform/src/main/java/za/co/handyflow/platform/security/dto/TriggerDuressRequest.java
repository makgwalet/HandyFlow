package za.co.handyflow.platform.security.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Duress trigger payload. Deliberately minimal — per Part 9.4 of the
 * design, a duress control "skips the normal severity/description form
 * entirely" for sub-second alerting. No required fields beyond the detail
 * itself; location is best-effort if the device has a GPS fix.
 */
public record TriggerDuressRequest(
        UUID       protectionDetailId,   // nullable — duress may be triggered with no active detail context
        UUID       triggeredByGuardId,
        BigDecimal latitude,
        BigDecimal longitude
) {}
