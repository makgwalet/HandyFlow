package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record CreateRiskRequest(

        @NotBlank(message = "Risk title is required")
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2 000 characters")
        String description,

        // SAFETY|FINANCIAL|SCHEDULE|TECHNICAL|LEGAL|ENVIRONMENTAL
        String category,

        @Min(value = 1, message = "Probability must be between 1 and 5")
        @Max(value = 5, message = "Probability must be between 1 and 5")
        int probability,

        @Min(value = 1, message = "Impact must be between 1 and 5")
        @Max(value = 5, message = "Impact must be between 1 and 5")
        int impact,

        @Size(max = 2000, message = "Mitigation plan must not exceed 2 000 characters")
        String mitigation,

        UUID ownerId,

        @Size(max = 255, message = "Owner name must not exceed 255 characters")
        String ownerName,

        LocalDate reviewDate,

        boolean isOhsa

) {}
