package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for overtime claims. */
public final class OvertimeDtos {

    private OvertimeDtos() {}

    public record Claim(UUID id, UUID employeeId, String employeeNumber, String employeeName, LocalDate workDate, String kind, int minutes,
                        BigDecimal multiplier, BigDecimal hourlyRate, BigDecimal amount, String status, Instant decidedAt, String decisionNote,
                        UUID payRunId, boolean canDecide) {}

    public record RangeRequest(UUID employeeId, LocalDate from, LocalDate to) {}

    public record Skipped(LocalDate date, String reason) {}

    /** What a request did: claims made or brought up to date, claims taken back because the hours went, and days that cannot be claimed. */
    public record RequestResult(int created, int updated, int withdrawn, List<Skipped> skipped, List<Claim> claims) {}

    public record NoteRequest(String note) {}
}
