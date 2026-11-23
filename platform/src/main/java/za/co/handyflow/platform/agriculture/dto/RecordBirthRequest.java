package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * offspringCount is the number born alive and stillbornCount the number born dead; either may be left out (zero), but not
 * both. The record becomes BIRTHED if any were born alive and STILLBORN if none were. (There used to be no way to say
 * stillborn, so every birth was stored as BIRTHED.)
 */
public record RecordBirthRequest(@NotNull LocalDate actualBirthDate, Integer offspringCount, Integer stillbornCount) {}
