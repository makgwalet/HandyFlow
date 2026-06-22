package za.co.handyflow.platform.invoicing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateRecurringScheduleRequest(
        // Either customerId OR walkinClientName — validated in service
        UUID customerId,

        @NotBlank @Size(max = 255)
        String title,

        String notes,

        @NotNull
        String frequency,            // DAILY | WEEKLY | MONTHLY | CUSTOM

        Integer frequencyDay,        // day-of-month for MONTHLY, day-of-week for WEEKLY
        Integer customIntervalDays,  // required when frequency = CUSTOM

        @NotNull
        Instant startDate,

        Instant endDate,             // null = run forever

        // Walk-in fields
        @Size(max = 255) String walkinClientName,
        String walkinClientEmail,
        @Size(max = 50)  String walkinClientPhone
) {}