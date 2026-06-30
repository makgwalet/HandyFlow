package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record AdvanceSurveyResponse(
        UUID    id,
        UUID    itineraryStopId,
        UUID    surveyedByGuardId,
        String  surveyedByGuardName,
        Instant surveyedAt,
        String  entryExitRoutesNotes,
        String  hazardsNoted,
        String  photoUrlsJson,
        boolean allClear
) {}
