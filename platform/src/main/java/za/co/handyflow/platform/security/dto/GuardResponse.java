package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * GuardResponse — full guard details returned by every guard endpoint.
 *
 * Added in Phase 0:
 *   status          — operational status (ACTIVE/ON_LEAVE/SUSPENDED/etc.)
 *   statusNote      — reason for the last status change (e.g. "suspended pending hearing")
 *   statusChangedAt — when the status last changed
 *   psiraExpiryDate — PSiRA registration expiry for compliance alerting
 *
 * WHY NOT include photoUrl for base64?
 * In dev mode, guard.getPhotoUrl() returns "PENDING_UPLOAD" for base64 captures.
 * The frontend shows a fallback avatar when photoUrl is null or "PENDING_UPLOAD".
 * In production, this will be a CDN URL (S3-backed), which is safe to return.
 */
public record GuardResponse(
        UUID      id,
        String    firstName,
        String    lastName,
        String    fullName,
        String    psiraNumber,
        String    idNumber,
        String    phone,
        String    photoUrl,
        String    grade,
        boolean   active,
        String    notes,
        Instant   createdAt,

        // Status workflow fields (V50)
        String    status,
        String    statusNote,
        Instant   statusChangedAt,
        LocalDate psiraExpiryDate
) {}
