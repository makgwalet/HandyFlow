package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlan;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlanFrequency;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlanStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentPlanResponse(
        UUID id,
        UUID caseId,
        PaymentPlanStatus status,
        BigDecimal totalAgreedAmount,
        BigDecimal installmentAmount,
        PaymentPlanFrequency frequency,
        LocalDate startDate,
        LocalDate nextDueDate,
        Integer numberOfInstallments,
        Integer installmentsPaid,
        String notes,
        Instant createdAt
) {
    public static PaymentPlanResponse of(PaymentPlan p) {
        return new PaymentPlanResponse(
                p.getId(), p.getCaseId(), p.getStatus(), p.getTotalAgreedAmount(), p.getInstallmentAmount(),
                p.getFrequency(), p.getStartDate(), p.getNextDueDate(), p.getNumberOfInstallments(),
                p.getInstallmentsPaid(), p.getNotes(), p.getCreatedAt());
    }
}
