package za.co.handyflow.platform.crm.dto;

import za.co.handyflow.platform.crm.domain.model.ActivityType;
import za.co.handyflow.platform.crm.domain.model.CustomerStatus;
import za.co.handyflow.platform.crm.domain.model.CustomerType;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CustomerResponse — the outbound DTO for a single customer.
 *
 * WHY expose updatedAt but not version?
 * updatedAt is business-meaningful ("when was this last changed?").
 * version is an internal Hibernate/optimistic-lock implementation
 * detail that the frontend should never see or rely on.
 *
 * WHY expose customerType and status?
 * The frontend needs to display badge labels ("Lead", "Inactive") and
 * conditionally disable the "New Booking" button for BLOCKED customers.
 * Hiding these from the response forces the frontend to hard-code
 * assumptions or make extra API calls.
 *
 * WHY include tags?
 * The customer list page needs to show tags inline without a second
 * round-trip.  Tags are small (strings), so including them is fine.
 *
 * ADDRESS:
 * We return Map<String, String> here (not the typed AddressRequest record)
 * because the response shape might include legacy data stored before the
 * typed DTO was introduced.  Keep the response flexible; restrict only the
 * input.
 */
public record CustomerResponse(
        UUID id,
        String name,
        String email,
        String phone,
        Map<String, String> address,
        String taxNumber,
        String notes,
        CustomerType customerType,
        CustomerStatus status,
        Set<String> tags,
        Instant createdAt,
        Instant updatedAt
) {}

// ── Nested records (same compilation unit for convenience) ──────────────────

