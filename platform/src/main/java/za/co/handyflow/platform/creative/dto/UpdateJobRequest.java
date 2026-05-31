package za.co.handyflow.platform.creative.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateJobRequest(
        String     title,
        String     description,
        String     brief,
        String     priority,
        LocalDate  dueDate,
        BigDecimal budget,
        BigDecimal quotedAmount,
        UUID       assignedTo,
        String     notes,
        String     clientEmail
) {}