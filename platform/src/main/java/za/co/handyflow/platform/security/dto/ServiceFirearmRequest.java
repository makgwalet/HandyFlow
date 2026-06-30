package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ServiceFirearmRequest(
        @NotNull LocalDate serviceDate,
        LocalDate nextDueDate
) {}
