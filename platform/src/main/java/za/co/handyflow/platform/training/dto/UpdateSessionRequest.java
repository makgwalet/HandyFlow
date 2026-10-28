package za.co.handyflow.platform.training.dto;

/** {@code openBooking} left out keeps the session as it is. */
public record UpdateSessionRequest(
        String venue,
        String trainerName,
        Integer capacity,
        String notes,
        Boolean openBooking
) {
    public UpdateSessionRequest(String venue, String trainerName, Integer capacity, String notes) {
        this(venue, trainerName, capacity, notes, null);
    }
}
