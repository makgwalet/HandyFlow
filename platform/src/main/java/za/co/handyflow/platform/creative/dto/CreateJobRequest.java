package za.co.handyflow.platform.creative.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateJobRequest(
        UUID       customerId,
        @NotBlank  String     clientName,
        String     clientEmail,
        @NotBlank  String     title,
        String     jobType,
        String     description,
        String     brief,
        String     priority,
        LocalDate  dueDate,
        BigDecimal budget,
        BigDecimal quotedAmount,
        UUID       assignedTo,
        String     notes
) {}