package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

/** userId is optional; when given, the person can't be rostered on overlapping shifts, across events too. */
public record StaffAssignmentRequest(
        UUID userId,
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank String kind,              // STAFF | VOLUNTEER | CONTRACTOR
        @NotBlank String role,              // COORDINATOR, STEWARD, REGISTRATION, SECURITY, FIRST_AID, AV_TECH, CATERING, DRIVER, VOLUNTEER_LEAD, OTHER
        @Size(max = 50) String phone,
        @Size(max = 254) String email,
        @Size(max = 200) String post,
        @NotNull LocalDateTime shiftStart,
        @NotNull LocalDateTime shiftEnd,
        @Size(max = 500) String notes
) {}