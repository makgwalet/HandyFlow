package za.co.handyflow.platform.bookings.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateStaffRequest(
        @NotBlank String name,
        String email,
        String phone,
        UUID employeeId
) {}