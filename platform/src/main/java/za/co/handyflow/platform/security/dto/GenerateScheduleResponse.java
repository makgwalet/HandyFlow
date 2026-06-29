package za.co.handyflow.platform.security.dto;

import java.time.LocalDate;
import java.util.UUID;

public record GenerateScheduleResponse(
        UUID patternId,
        String patternName,
        LocalDate fromDate,
        LocalDate toDate,
        int shiftsCreated,
        int shiftsSkipped,      // e.g. guard on leave, shift already exists
        java.util.List<String> warnings   // e.g. "Guard Kabelo Sithole is ON_LEAVE — skipped 3 shifts"
) {}