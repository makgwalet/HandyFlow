// security/dto/GuardResponse.java
package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * GuardResponse — CHANGE (V214): added employeeCode, appended at the end.
 *
 * NOTE: I do not have this file's previously-existing content -- reconstructed
 * from GuardService.toResponse()'s 16-argument constructor call, which has
 * compiled successfully in this codebase before this change. Diff against
 * your actual file before applying; if it differs, tell me what's actually
 * in it (same situation PrincipalResponse was in earlier this session).
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
        String    status,
        String    statusNote,
        Instant   statusChangedAt,
        LocalDate psiraExpiryDate,
        String    employeeCode,
        String    emergencyContactName,
        String    emergencyContactPhone,
        // FIX (Security P4 — VettingController now has a frontend):
        // cpVettingTier has always existed on the Guard entity (set via
        // VettingController's POST /officers/{guardId}/tier) but was
        // never exposed through this response — meaning the tier could
        // be SET through the API but never SEEN again anywhere. Appended
        // at the end, matching this record's own established
        // convention (see the V214 employeeCode addition above).
        String    cpVettingTier
) {}