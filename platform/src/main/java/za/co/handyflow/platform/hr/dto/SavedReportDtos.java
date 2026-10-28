package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SavedReportDtos {

    private SavedReportDtos() {}

    public record SaveRequest(String name, String kind, String period, String schedule, Integer runDay, Boolean emailReminder) {
        /** Without the email option, as before. */
        public SaveRequest(String name, String kind, String period, String schedule, Integer runDay) { this(name, kind, period, schedule, runDay, false); }
        public boolean email() { return Boolean.TRUE.equals(emailReminder); }
    }

    public record ShareRequest(List<UUID> userIds) {}

    /** A colleague a report can be shared with. */
    public record Colleague(UUID id, String name) {}

    /** {@code from} and {@code to} are what the period means today, so the screen can open the report without working dates out itself. */
    public record View(UUID id, String name, String kind, String kindLabel, String period, String periodLabel, String schedule, Integer runDay,
                       LocalDate from, LocalDate to, LocalDate lastNotifiedOn, Instant createdAt,
                       boolean emailReminder, boolean shared, String ownerName, List<UUID> sharedWith) {
        /** Own report, shared with nobody, no email. */
        public View(UUID id, String name, String kind, String kindLabel, String period, String periodLabel, String schedule, Integer runDay,
                    LocalDate from, LocalDate to, LocalDate lastNotifiedOn, Instant createdAt) {
            this(id, name, kind, kindLabel, period, periodLabel, schedule, runDay, from, to, lastNotifiedOn, createdAt, false, false, null, List.of());
        }
    }
}
