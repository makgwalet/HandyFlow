package za.co.handyflow.platform.recruitmentagency.dto;

import java.util.UUID;

public record PortalAuthResponse(String token, UUID portalUserId, String email, String fullName) {}