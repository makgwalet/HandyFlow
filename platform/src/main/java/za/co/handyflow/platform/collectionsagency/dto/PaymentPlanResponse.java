package za.co.handyflow.platform.collectionsagency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentPlanResponse(
        UUID id, UUID debtorAccountId, String status, BigDecimal totalAgreedAmount, BigDecimal installmentAmount,
        String frequency, LocalDate startDate, LocalDate nextDueDate, Integer numberOfInstallments,
        Integer installmentsPaid, String notes
) {}
