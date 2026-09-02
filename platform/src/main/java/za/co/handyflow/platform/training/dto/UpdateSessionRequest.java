package za.co.handyflow.platform.training.dto;

public record UpdateSessionRequest(
        String venue,
        String trainerName,
        Integer capacity,
        String notes
) {}
