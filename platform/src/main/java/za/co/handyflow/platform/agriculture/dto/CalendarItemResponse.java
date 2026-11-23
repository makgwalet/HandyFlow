package za.co.handyflow.platform.agriculture.dto;

import java.time.LocalDate;
import java.util.UUID;

/** One line on the farm calendar. {@code targetType} is ANIMAL, GROUP or CROP_CYCLE; {@code targetId} opens it. */
public record CalendarItemResponse(
        LocalDate date,
        String kind,        // HEALTH_DUE | HEALTH_SCHEDULED | WITHDRAWAL_END | BIRTH_DUE | PLANTING | HARVEST | SCOUTING_FOLLOWUP
        String title,
        String detail,
        String targetType,
        UUID targetId,
        boolean overdue     // something to be done whose day has passed
) {}
