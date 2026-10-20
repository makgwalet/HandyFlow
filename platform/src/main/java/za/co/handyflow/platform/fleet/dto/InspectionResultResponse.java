package za.co.handyflow.platform.fleet.dto;

import java.util.List;
import java.util.UUID;

public record InspectionResultResponse(
        UUID id,
        String section,
        String label,
        int sortOrder,
        boolean required,
        boolean photoRequiredOnFail,
        String severity,
        String outcome,
        String comment,
        List<UUID> photoIds
) {}