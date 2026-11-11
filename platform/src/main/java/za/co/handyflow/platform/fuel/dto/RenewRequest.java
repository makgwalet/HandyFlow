package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RenewRequest(@NotNull LocalDate newExpiresOn, LocalDate issuedOn, @Size(max = 200) String note) {}
