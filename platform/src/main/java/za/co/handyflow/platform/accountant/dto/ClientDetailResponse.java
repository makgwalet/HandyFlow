package za.co.handyflow.platform.accountant.dto;

import java.util.List;

/**
 * Closes the "unified client detail page" gap — deadlines, fee notes,
 * journals, and time entries for one client in a single response,
 * matching PortfolioDashboardResponse's own aggregate pattern but
 * scoped to one client instead of the whole portfolio.
 * <p>
 * Each list is capped to the most recent entries, not a full paginated
 * history — this is an overview, not a replacement for the Compliance/
 * Billing/Journals/Time tabs' own full, filterable views. A client with
 * years of history should still go to those tabs for complete detail;
 * this exists to answer "what's going on with this client right now"
 * in one place, which was the actual problem named in the audit.
 */
public record ClientDetailResponse(
        ClientResponse client,
        List<TaxDeadlineResponse> recentDeadlines,
        List<FeeNoteResponse> recentFeeNotes,
        List<JournalResponse> recentJournals,
        List<TimeEntryResponse> recentTimeEntries
) {
}