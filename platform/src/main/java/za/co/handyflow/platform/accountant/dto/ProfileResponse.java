package za.co.handyflow.platform.accountant.dto;

public record ProfileResponse(
        java.util.UUID id,
        String firmName,
        String practiceNumber,
        String registrationNumber,
        String vatNumber,
        String contactEmail,
        String contactPhone,
        java.math.BigDecimal defaultHourlyRate,
        int yearEndMonth,
        java.time.Instant createdAt
) {}
