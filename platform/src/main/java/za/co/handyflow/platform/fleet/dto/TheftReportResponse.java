package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;

public record TheftReportResponse(
        String kind,
        Instant occurredAt,
        String location,
        String policeCaseNumber,
        String sapsStation,
        String policeReference,
        String insurer,
        String claimNumber,
        String trackingInfo,
        String notes,
        String recoveryStatus,
        Instant recoveredAt,
        String recoveryLocation,
        String recoveryNotes
) {}