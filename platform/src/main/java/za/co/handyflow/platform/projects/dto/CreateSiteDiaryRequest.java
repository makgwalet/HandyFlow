package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSiteDiaryRequest(

        @NotNull(message = "Diary date is required")
        LocalDate diaryDate,

        @Size(max = 100, message = "Weather must not exceed 100 characters")
        String weather,             // CLEAR|OVERCAST|RAIN|WIND|EXTREME — or free text

        @DecimalMin(value = "-50.0", message = "Temperature seems too low")
        @DecimalMax(value =  "60.0", message = "Temperature seems too high")
        BigDecimal tempCelsius,

        @PositiveOrZero(message = "Workers present cannot be negative")
        @Max(value = 9999, message = "Workers present count is unrealistically high")
        int workersPresent,

        @PositiveOrZero(message = "Workers planned cannot be negative")
        Integer workersPlanned,     // nullable — not always known up front

        @Size(max = 5000, message = "Work description must not exceed 5 000 characters")
        String workDescription,

        @Size(max = 2000, message = "Progress notes must not exceed 2 000 characters")
        String progressNotes,

        @Size(max = 2000, message = "Issues must not exceed 2 000 characters")
        String issues,

        @Size(max = 1000, message = "Visitor names must not exceed 1 000 characters")
        String visitorNames,

        @Size(max = 2000, message = "Incidents must not exceed 2 000 characters")
        String incidents,

        @Size(max = 500, message = "Toolbox topic must not exceed 500 characters")
        String toolboxTopic,

        @Size(max = 1000, message = "Equipment notes must not exceed 1 000 characters")
        String equipmentNotes

) {}
