package za.co.handyflow.platform.events.dto;

import java.util.List;
import java.util.UUID;

public record GroupRegistrationResponse(
        UUID groupRef,
        int count,
        List<GuestResponse> guests
) {}