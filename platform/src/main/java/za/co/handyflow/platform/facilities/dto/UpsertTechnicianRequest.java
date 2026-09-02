package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record UpsertTechnicianRequest(
        @NotBlank String name, String contactPhone, String contactEmail,
        String specialization, UUID linkedUserId
) {}
