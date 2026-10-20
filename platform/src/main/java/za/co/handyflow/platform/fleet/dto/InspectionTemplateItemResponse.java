package za.co.handyflow.platform.fleet.dto;

import java.util.UUID;

public record InspectionTemplateItemResponse(
        UUID id,
        String section,
        String label,
        int sortOrder,
        boolean required,
        boolean photoRequiredOnFail,
        String severity
) {}