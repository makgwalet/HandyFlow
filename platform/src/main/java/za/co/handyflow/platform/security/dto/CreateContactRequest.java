package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateContactRequest(
        UUID siteId,       // nullable -- tenant-wide contact (e.g. "Police") when omitted
        @NotBlank String name,
        @NotBlank String role, // SITE_MANAGER | CLIENT_CONTACT | SECURITY_MANAGER | CONTROL_ROOM | POLICE | AMBULANCE | FIRE | OTHER
        String phone,
        String email
) {}
