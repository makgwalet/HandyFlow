package za.co.handyflow.platform.fleet.dto;

import java.util.List;

/** The Driver 360 view: who they are, document compliance, performance score, activity, accidents and cost. */
public record DriverProfileResponse(
        DriverResponse driver,
        List<ComplianceItemResponse> compliance,     // driving licence, and PrDP when required
        DriverScoreResponse score,
        DriverActivityResponse activity,
        DriverAccidentSummaryResponse accidents
) {}