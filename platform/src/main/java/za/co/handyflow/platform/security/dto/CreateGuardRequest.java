package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * CreateGuardRequest — also used for updateGuard (reuse pattern consistent with Bookings).
 *
 * Added psiraExpiryDate so the compliance tracking added in V102 can actually
 * be populated.  Without this field, the column exists but can never be set
 * through the API — the psiraExpiryDate compliance badge in GuardsTab would
 * never show a real date.
 */
public record CreateGuardRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String     psiraNumber,
        String     idNumber,
        String     phone,
        String     grade,
        String     notes,
        LocalDate  psiraExpiryDate
) {}
