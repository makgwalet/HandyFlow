package za.co.handyflow.platform.earthmoving.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Everything the checklist screen needs for one inspection type: what is in force, the draft being written (if any) and the history. */
public record ChecklistTypeOverview(String inspectionType, ActiveChecklistResponse active, ChecklistVersionResponse draft, List<Past> history) {
    public record Past(UUID id, int versionNo, String status, LocalDate effectiveFrom, Instant publishedAt, int questions) {}
}
