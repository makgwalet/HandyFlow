package za.co.handyflow.platform.agriculture.dto;

import java.time.LocalDate;
import java.util.UUID;

// FIX (Agriculture GAP 5 — mobile gap report): a unified, flat shape
// across three genuinely different source entities (health events,
// scouting follow-ups, low-stock inventory) specifically so the mobile
// app can render one ranked list without merging three typed lists
// itself — the gap report's own stated want ("ranked by severity" in
// one list). severity is computed server-side: OVERDUE (dueDate in the
// past) ranks above DUE_TODAY; low-stock items (no dueDate) rank as
// MEDIUM by default — genuinely urgent but not date-driven the way the
// other two are.
public record AttentionItemResponse(
        String type,          // HEALTH_EVENT_DUE | SCOUTING_FOLLOWUP_DUE | LOW_STOCK
        String severity,       // OVERDUE | DUE_TODAY | MEDIUM
        String title,
        String description,
        LocalDate dueDate,     // null for LOW_STOCK items
        UUID referenceId       // the health event / scouting record / inventory item id
) {}
