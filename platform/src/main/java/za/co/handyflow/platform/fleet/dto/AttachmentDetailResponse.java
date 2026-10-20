package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AttachmentDetailResponse(
        AttachmentResponse summary,
        String description,
        LocalDate purchaseDate,
        BigDecimal purchasePrice,
        BigDecimal costPerHour,          // purchase price over lifetime hours in use; null until it has been used
        String notes,
        List<AttachmentAssignmentResponse> history      // newest first
) {}