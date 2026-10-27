package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** {@code linkedUserId} ties the technician to a system user; it determines which jobs that user can access. Null unlinks. */
public record UpsertFmTechnicianRequest(
        @NotBlank String name, String contactPhone, String contactEmail, String specialization, UUID linkedUserId
) {}