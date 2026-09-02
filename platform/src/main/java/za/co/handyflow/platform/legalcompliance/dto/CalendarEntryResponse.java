package za.co.handyflow.platform.legalcompliance.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One entry in the aggregated Legal/Compliance calendar — combines
 * regulatory obligation review dates, litigation matter key dates, and
 * (via ContractingFacade, read-only) contract renewal/expiry dates into one
 * chronological view. sourceType + sourceId let the frontend deep-link back
 * to the originating record; this response never duplicates full record
 * detail, only what a calendar view needs.
 */
public record CalendarEntryResponse(
        LocalDate date,
        String sourceType,   // "OBLIGATION" | "LITIGATION" | "CONTRACT_RENEWAL"
        UUID sourceId,
        String title,
        String detail
) {}
