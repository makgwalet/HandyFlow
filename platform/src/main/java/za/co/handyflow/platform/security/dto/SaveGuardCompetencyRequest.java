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
        String notes,
        String firearmCategory             // HANDGUN | SHOTGUN | RIFLE: required for FIREARM_COMPETENCY
) {
    /** Keeps callers that do not know about categories working. */
    public SaveGuardCompetencyRequest(String competencyType, String title, String issuedBy, LocalDate issueDate,
                                      LocalDate expiryDate, String certificateRef, boolean required, String notes) {
        this(competencyType, title, issuedBy, issueDate, expiryDate, certificateRef, required, notes, null);
    }
}
