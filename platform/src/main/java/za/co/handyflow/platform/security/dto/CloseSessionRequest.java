package za.co.handyflow.platform.security.dto;

public record CloseSessionRequest(
        boolean pinVerified,
        Double faceMatchConfidence,
        String handoverNotes,
        boolean resourcesReturned,
        String incompletePatrolReason   // required if patrol rounds are missed
) {}