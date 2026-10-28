package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class SavedReportDtos {

    private SavedReportDtos() {}

    public record SaveRequest(String name, String kind, String period, String schedule, Integer runDay) {}

    /** {@code from} and {@code to} are what the period means today, so the screen can open the report without working dates out itself. */
    public record View(UUID id, String name, String kind, String kindLabel, String period, String periodLabel, String schedule, Integer runDay,
                       LocalDate from, LocalDate to, LocalDate lastNotifiedOn, Instant createdAt) {}
}
