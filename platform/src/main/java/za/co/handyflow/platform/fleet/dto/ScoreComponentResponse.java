package za.co.handyflow.platform.fleet.dto;

public record ScoreComponentResponse(
        String key,                 // SAFETY | INSPECTION | ACCIDENTS | FUEL | TRIPS | COMPLIANCE
        String label,
        int weight,                 // configured weight
        double effectiveWeight,     // weight actually applied after redistributing from NO_DATA components; 0 if NO_DATA
        Integer score,              // 0–100, null when NO_DATA
        String status,              // SCORED | NO_DATA
        String detail               // the numbers behind the score, or why there is no data
) {}