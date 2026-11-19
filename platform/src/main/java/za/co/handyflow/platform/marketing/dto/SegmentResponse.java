package za.co.handyflow.platform.marketing.dto;

import java.time.Instant;
import java.util.UUID;

public record SegmentResponse(
        UUID              id,
        String            name,
        String            description,
        String            type,
        SegmentDefinition definition,
        Integer           lastCount,          // eligible contacts at lastCountedAt; null until first computed
        Instant           lastCountedAt,
        long              usedByActiveCampaigns,
        Instant           createdAt,
        Instant           updatedAt
) {}