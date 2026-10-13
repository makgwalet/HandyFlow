package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** The amount is calculated from the approved rule and its limits — callers never type a fee amount. */
public record ChargeFeeRequest(@NotBlank String ruleCode, LocalDate effectiveDate, String notes) {}
