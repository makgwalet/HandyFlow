package za.co.handyflow.platform.auditor.dto;

import java.util.UUID;

// FIX: was incorrectly assumed to be za.co.handyflow.platform.shared.PortalAuthResponse
// — the compiler confirmed no such shared type exists. Every other
// portal auth service almost certainly defines its own local
// equivalent of this exact shape, matching the frontend's own
// convention of a local `interface PortalAuthResponse` per portal
// rather than importing a shared one. This is that local equivalent
// for the auditor module specifically.
public record AuditorPortalAuthResponse(
        String token,
        UUID portalUserId,
        String email,
        String fullName
) {}