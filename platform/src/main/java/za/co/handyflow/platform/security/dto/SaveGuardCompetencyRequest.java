// security/dto/SaveGuardCompetencyRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record SaveGuardCompetencyRequest(
        @NotBlank String competencyType,   // FIREARM_COMPETENCY | FIRST_AID | ... | OTHER
        String title,                      // required when the type is OTHER
        String issuedBy,
        LocalDate issueDate,
        LocalDate expiryDate,
        String certificateRef,
        boolean required,
        String notes
) {}
