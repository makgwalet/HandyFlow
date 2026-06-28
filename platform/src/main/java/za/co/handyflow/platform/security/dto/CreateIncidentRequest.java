package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CreateIncidentRequest — request body for POST /api/v1/security/incidents
 *
 * Added `type` field (fixes bug #16).
 *
 * The V12 migration created a type CHECK constraint (THEFT, TRESPASS, MEDICAL,
 * FIRE, VANDALISM, ASSAULT, SUSPICIOUS, OTHER) but neither this DTO nor
 * IncidentService ever set it — every incident defaulted to type='GENERAL'
 * (added in V48).  This meant incident analytics by type (the standard
 * feature competitors advertise) produced meaningless data.
 *
 * type is optional — defaults to GENERAL in IncidentService if omitted,
 * so existing integrations that don't send it are backward-compatible.
 */
public record CreateIncidentRequest(
        @NotNull UUID    siteId,
        UUID             shiftId,
        UUID             guardId,
        @NotBlank String title,
        String           description,

        @NotNull
        @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL",
                message = "severity must be LOW, MEDIUM, HIGH or CRITICAL")
        String severity,

        /**
         * Incident type — what kind of event occurred.
         * Optional: defaults to GENERAL if not provided.
         * Valid values: THEFT, TRESPASS, MEDICAL, FIRE, VANDALISM,
         *               ASSAULT, SUSPICIOUS, GENERAL, OTHER
         */
        @Pattern(regexp = "THEFT|TRESPASS|MEDICAL|FIRE|VANDALISM|ASSAULT|SUSPICIOUS|GENERAL|OTHER",
                message = "type must be one of: THEFT, TRESPASS, MEDICAL, FIRE, VANDALISM, " +
                        "ASSAULT, SUSPICIOUS, GENERAL, OTHER")
        String type,

        BigDecimal latitude,
        BigDecimal longitude
) {}
