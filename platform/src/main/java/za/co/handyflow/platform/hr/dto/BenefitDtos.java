package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class BenefitDtos {

    private BenefitDtos() {}

    public record AddRequest(String kind, BigDecimal monthlyAmount, LocalDate effectiveFrom, LocalDate effectiveTo, String description, String reason) {}

    public record EndRequest(LocalDate lastDay) {}

    public record View(UUID id, UUID employeeId, String kind, String kindLabel, BigDecimal monthlyAmount, LocalDate effectiveFrom, LocalDate effectiveTo,
                       String description, String reason, boolean inForce, Instant createdAt) {}
}
