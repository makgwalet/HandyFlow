package za.co.handyflow.platform.payrollbureau.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayClientResponse(
        UUID id,
        String tradingName,
        String registrationNumber,
        String payeReference,
        String uifReference,
        String sdlReference,
        String payFrequency,
        Integer payDay,
        String contactName,
        String contactEmail,
        String contactPhone,
        LocalDate onboardedAt,
        String status,
        String notes,
        Instant createdAt
) {}