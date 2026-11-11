package za.co.handyflow.platform.events.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Carries phone and email — the roster is the on-the-day contact list, so it needs the ops permission, not plain read. */
public record StaffAssignmentResponse(
        UUID id,
        UUID userId,
        String fullName,
        String kind,
        String role,
        String phone,
        String email,
        String post,
        LocalDateTime shiftStart,
        LocalDateTime shiftEnd,
        String status,                 // ASSIGNED | CONFIRMED | ARRIVED | NO_SHOW | CANCELLED
        Instant arrivedAt,
        String notes
) {}