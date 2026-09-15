package za.co.handyflow.platform.agriculture.dto;

import java.util.List;
import java.util.UUID;

// FIX (Agriculture GAP 4 — mobile gap report): the real
// GET /farms/{id}/today-summary endpoint, one round trip instead of the
// N the gap report's own documented workaround needed. Deliberately
// counts-only for animals/groups/crop cycles — no eggs/birds tiles,
// since Poultry is confirmed out of scope for this build increment
// (see package-info.java), and no hectares-in-production figure this
// pass — that needs summing AgProductionArea.sizeHectares across every
// active crop cycle's production area, a genuinely separate join this
// endpoint doesn't attempt yet rather than approximating it.
public record FarmTodaySummaryResponse(
        UUID farmId,
        long activeAnimalCount,
        long activeGroupCount,
        long activeCropCycleCount,
        List<AttentionItemResponse> attentionItems
) {}
