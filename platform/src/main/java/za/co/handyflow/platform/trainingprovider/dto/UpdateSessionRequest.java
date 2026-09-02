package za.co.handyflow.platform.trainingprovider.dto;

public record UpdateSessionRequest(
        String venue,
        String trainerName,
        Integer capacity,
        String notes
) {}
