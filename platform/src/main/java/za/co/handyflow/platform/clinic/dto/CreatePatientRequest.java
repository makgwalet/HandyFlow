package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePatientRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String idNumber,
        LocalDate dateOfBirth,
        String gender,
        String phone,
        String email,
        String emergencyContactName,
        String emergencyContactPhone,
        // P5 family account fields — all optional, null = individual account
        String accountType,    // INDIVIDUAL | PRINCIPAL | DEPENDANT
        UUID   principalId,    // non-null when accountType = DEPENDANT
        String relationship    // CHILD | PARENT | GRANDPARENT | SPOUSE | SIBLING | OTHER
) {}
