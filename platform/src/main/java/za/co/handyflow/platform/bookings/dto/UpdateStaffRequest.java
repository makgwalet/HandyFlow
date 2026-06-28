package za.co.handyflow.platform.bookings.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateStaffRequest(
        @NotBlank String name,
        String email,
        String phone
) {}
