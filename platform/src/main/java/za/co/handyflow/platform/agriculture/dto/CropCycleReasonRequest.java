package za.co.handyflow.platform.agriculture.dto;

/** Shared body shape for the markFailed/abandon transitions — both take an optional free-text reason. */
public record CropCycleReasonRequest(
        String reason
) {}
