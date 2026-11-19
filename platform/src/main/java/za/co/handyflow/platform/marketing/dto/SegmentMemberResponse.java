package za.co.handyflow.platform.marketing.dto;

import java.time.Instant;
import java.util.UUID;

public record SegmentMemberResponse(
        UUID    preferenceId,
        String  email,
        String  name,
        boolean optedIn,
        boolean suppressed,
        Instant addedAt
) {}