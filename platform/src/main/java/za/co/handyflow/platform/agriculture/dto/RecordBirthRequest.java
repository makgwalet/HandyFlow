package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RecordBirthRequest(@NotNull LocalDate actualBirthDate, Integer offspringCount) {}
