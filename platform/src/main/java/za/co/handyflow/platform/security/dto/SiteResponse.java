package za.co.handyflow.platform.security.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * SiteResponse — full site details including contract lifecycle.
 *
 * Added in Phase 0:
 *   terminatedAt — when the contract was terminated (fixes bug #9).
 *
 * WHY was terminatedAt missing?
 * V48 added the column; Site.java has the field.  SiteResponse and
 * SiteService.toResponse() simply never mapped it, so the timestamp was
 * captured in the DB but invisible to every API consumer.
 * For audit and legal purposes, "when was this contract terminated" is as
 * important as "why was it terminated".
 */
public record SiteResponse(
        UUID       id,
        String     name,
        UUID       customerId,
        Object     address,
        BigDecimal latitude,
        BigDecimal longitude,
        String     contactName,
        String     contactPhone,
        boolean    active,
        List<CheckpointResponse> checkpoints,
        String     contractStatus,
        LocalDate  contractStart,
        LocalDate  contractEnd,
        String     terminationReason,
        Instant    terminatedAt,      // ← added (bug #9 fix)
        Instant    createdAt
) {}
