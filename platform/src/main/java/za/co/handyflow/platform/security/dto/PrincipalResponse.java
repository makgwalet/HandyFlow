// security/dto/PrincipalResponse.java
package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * PrincipalResponse — full principal detail, only ever returned to callers
 * already gated behind VIP_DETAIL_ACCESS (see CloseProtectionController).
 *
 * CHANGE: added vettingStatus (V213 follow-up) -- Principal.vettingStatus
 * was added and persisted last session, but this response record was never
 * updated to expose it, so GET /cp/principals/{id} still couldn't return it
 * to any caller despite the data existing. Appended at the end rather than
 * inserted between existing fields, to avoid breaking any other positional
 * constructor call to this record elsewhere in the codebase.
 *
 * Confirmed against the actual file: field is emergencyContactsJson, not
 * emergencyContacts as initially guessed from usage alone -- fixed.
 */
public record PrincipalResponse(
        UUID    id,
        String  fullName,
        String  aliasCodename,
        String  threatLevel,
        String  medicalNotes,
        String  knownThreats,
        String  emergencyContactsJson,
        String  photoUrl,
        boolean active,
        Instant createdAt,
        String  vettingStatus
) {}