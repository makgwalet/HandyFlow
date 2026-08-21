package za.co.handyflow.platform.hr.dto;

import java.util.UUID;

public record HrPortalAuthResponse(
        String token,
        UUID portalUserId,
        String email,
        String fullName
) {}