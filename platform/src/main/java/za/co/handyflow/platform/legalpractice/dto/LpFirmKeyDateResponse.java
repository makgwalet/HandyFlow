package za.co.handyflow.platform.legalpractice.dto;

import java.time.LocalDate;
import java.util.UUID;

/** A key date together with the matter it belongs to, for the firm-wide calendar. */
public record LpFirmKeyDateResponse(
        UUID id,
        UUID matterId,
        String matterNumber,
        String matterName,
        String matterStatus,
        String dateType,
        LocalDate dueDate,
        String description,
        boolean acknowledged,
        String status
) {}
