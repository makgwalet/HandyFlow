package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePrincipalRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Size(max = 50)  String aliasCodename,
        String threatLevel,
        String medicalNotes,
        String knownThreats,
        String emergencyContactsJson
) {}
