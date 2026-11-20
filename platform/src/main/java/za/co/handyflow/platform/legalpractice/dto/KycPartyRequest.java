package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

/** verifiedOn is the date the person's identity was verified; leave it out if that has not been done yet. method is OWNERSHIP, CONTROL or MANAGEMENT for a beneficial owner. */
public record KycPartyRequest(@NotBlank String role, @NotBlank String name, String idNumber, BigDecimal percent, String method,
                              String pep, LocalDate verifiedOn, String note) {
}
