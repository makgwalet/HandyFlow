package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What a client contact may see of a work order. Deliberately EXCLUDES cost
 * (the FM company's internal cost/margin), technician and vendor identities
 * (subcontractor information), completion/hold/cancellation notes (written
 * by staff for internal use) and the invoiced flag. A client-visible
 * completion summary should be added later as its own explicit field rather
 * than exposing internal notes.
 */
public record FmPortalWorkOrderResponse(
        UUID id, String workOrderNumber, UUID siteId, UUID assetId,
        String category, String priority, String status, String description, String reportedBy,
        LocalDate scheduledDate, Instant completedAt, Instant createdAt
) {}