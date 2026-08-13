package za.co.handyflow.platform.bookingagency.dto;

import java.time.LocalTime;
import java.util.UUID;

public record ResourceResponse(
        UUID id, UUID clientId, String name, String roleDescription,
        LocalTime workingHoursStart, LocalTime workingHoursEnd, boolean active
) {}