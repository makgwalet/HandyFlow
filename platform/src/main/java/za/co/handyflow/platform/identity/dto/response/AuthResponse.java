package za.co.handyflow.platform.identity.dto.response;

import java.util.Set;
import java.util.UUID;

public record  AuthResponse(
        String accessToken,
        String tokenType,      // Always "Bearer"
        long expiresIn,        // Seconds until expiry
        UUID userId,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        Set<String> permissions,
        // NEW: LoginPage.tsx already reads data.subscriptionStatus to
        // decide whether to redirect straight to AccountLockedPage
        // instead of the dashboard — that page is fully built and
        // waiting on this field, which simply never existed on the
        // response it depends on, so the redirect could never fire.
        // Sourced from Tenant.status directly (TRIAL/ACTIVE/SUSPENDED/
        // CANCELLED) — matches exactly the two values LoginPage.tsx
        // checks for. Deeper distinctions (e.g. PAST_DUE) live on the
        // billing module's own Subscription entity and are resolved
        // separately by AccountLockedPage itself after the redirect —
        // this field only needs to answer "should we redirect at all".
        String subscriptionStatus
) {}