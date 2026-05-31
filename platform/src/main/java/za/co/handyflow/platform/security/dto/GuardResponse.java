package za.co.handyflow.platform.security.dto;
import java.time.Instant; import java.util.UUID;
public record GuardResponse(
        UUID id, String firstName, String lastName, String fullName,
        String psiraNumber, String idNumber, String phone,
        String photoUrl, String grade, boolean active,
        String notes, Instant createdAt
) {}
