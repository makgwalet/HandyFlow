package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.TheftKind;

import java.time.Instant;

/** Report a vehicle stolen, or missing (whereabouts unknown). Police and insurer details can be added later with PUT. */
public record ReportTheftRequest(
        @NotNull TheftKind kind,
        @NotNull Instant occurredAt,             // when it was stolen / last seen
        @Size(max = 500) String location,
        @Size(max = 100) String policeCaseNumber,
        @Size(max = 255) String sapsStation,
        @Size(max = 100) String policeReference,
        @Size(max = 255) String insurer,
        @Size(max = 100) String claimNumber,
        String trackingInfo,                     // last known position, tracker status, provider contact...
        String notes
) {}