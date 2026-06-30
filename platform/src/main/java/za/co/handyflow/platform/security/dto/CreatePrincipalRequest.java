package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePrincipalRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Size(max = 50)  String aliasCodename,
        String threatLevel,        // LOW | MEDIUM | HIGH | CRITICAL — defaults to LOW
        String medicalNotes,
        String knownThreats,
        String emergencyContactsJson   // raw JSON array string, validated/parsed by the service
) {}
