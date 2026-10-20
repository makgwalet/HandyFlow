package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionDetailResponse(
        InspectionResponse summary,
        UUID driverId,
        String notes,
        boolean signatureRequired,
        boolean signatureRecorded,
        String signerName,
        Instant signedAt,
        List<InspectionResultResponse> results,
        List<InspectionPhotoResponse> photos
) {}