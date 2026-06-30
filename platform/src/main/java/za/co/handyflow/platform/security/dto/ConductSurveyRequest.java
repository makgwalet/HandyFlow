package za.co.handyflow.platform.security.dto;

public record ConductSurveyRequest(
        String entryExitRoutesNotes,
        String hazardsNoted,
        String photoUrlsJson,    // raw JSON array string
        boolean allClear
) {}
