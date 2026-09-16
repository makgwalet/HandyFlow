package za.co.handyflow.platform.security.dto;

import java.util.UUID;

public record SecurityContactResponse(
        UUID id, UUID siteId, String name, String role, String phone, String email, boolean active
) {}
