package za.co.handyflow.platform.bookings.dto;

import java.util.UUID;

public record StaffResponse(
        UUID id,
        String name,
        String email,
        String phone,
        UUID employeeId,
        boolean active
) {}