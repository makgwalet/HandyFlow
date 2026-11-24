package za.co.handyflow.platform.complianceservices.dto;

import java.time.LocalDate;
import java.util.List;

public final class DeadlineGenerationDtos {

    private DeadlineGenerationDtos() {}

    public record KeyDatesRequest(Integer financialYearEndMonth, LocalDate registeredOn) {}

    public record KeyDatesView(Integer financialYearEndMonth, LocalDate registeredOn) {}

    public record GenerateRequest(Integer months) {}

    public record Skipped(String code, String reason) {}

    public record GenerationResult(LocalDate from, LocalDate to, int created, int cancelled, int unchanged, List<Skipped> skipped) {}
}
