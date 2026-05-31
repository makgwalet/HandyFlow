package za.co.handyflow.platform.creative.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JobResponse(
        UUID       id,
        UUID       customerId,
        String     clientName,
        String     clientEmail,
        String     title,
        String     jobType,
        String     description,
        String     brief,
        String     status,
        String     priority,
        LocalDate  dueDate,
        BigDecimal budget,
        BigDecimal quotedAmount,
        UUID       invoiceId,
        String     notes,
        UUID       assignedTo,
        int        proofCount,
        int        deliverableCount,
        Instant    createdAt,
        Instant    updatedAt
) {}