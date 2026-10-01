package za.co.handyflow.platform.agriculture.dto;

import java.time.LocalDate;
import java.util.UUID;

// FIX (Agriculture GAP 5 — mobile gap report): a unified, flat shape across genuinely different source entities
// (health events, scouting, inventory, crop harvests) so a client can render one ranked list without merging typed
// lists itself — the gap report's own stated want ("ranked by severity" in one list). severity is computed
// server-side, see AgAttentionRules for the ladder and the rules behind it.
//
// farmId / farmName were added so the tenant-wide dashboard can show which farm an item belongs to. They are additive:
// existing clients that ignore unknown JSON fields are unaffected.
public record AttentionItemResponse(
        String type,           // HEALTH_EVENT_DUE | SCOUTING_FOLLOWUP_DUE | SCOUTING_HIGH_SEVERITY | LOW_STOCK | HARVEST_DUE
        String severity,       // CRITICAL | OVERDUE | DUE_TODAY | UPCOMING | MEDIUM
        String title,
        String description,
        LocalDate dueDate,     // null for LOW_STOCK items and for high-severity scouting with no follow-up date
        UUID referenceId,      // the health event / scouting record / inventory item / crop cycle id
        UUID farmId,
        String farmName
) {}
