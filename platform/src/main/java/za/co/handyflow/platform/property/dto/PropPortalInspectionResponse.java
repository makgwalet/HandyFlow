package za.co.handyflow.platform.property.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PropPortalInspectionResponse(
        UUID id,
        String type,
        Instant inspectedAt,
        String inspectedBy,
        String overallCondition,
        String notes,
        List<Map<String, String>> items,
        List<String> photoUrls
) {}
