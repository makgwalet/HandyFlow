package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.Size;

/** Full replace of the details on the vehicle's open theft report. */
public record UpdateTheftReportRequest(
        @Size(max = 500) String location,
        @Size(max = 100) String policeCaseNumber,
        @Size(max = 255) String sapsStation,
        @Size(max = 100) String policeReference,
        @Size(max = 255) String insurer,
        @Size(max = 100) String claimNumber,
        String trackingInfo,
        String notes
) {}