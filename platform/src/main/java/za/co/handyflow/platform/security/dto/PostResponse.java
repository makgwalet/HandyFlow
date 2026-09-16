package za.co.handyflow.platform.security.dto;

import java.util.UUID;

public record PostResponse(UUID id, UUID siteId, String name, String description, boolean active) {}
