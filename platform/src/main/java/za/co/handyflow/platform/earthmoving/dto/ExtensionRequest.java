package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** A hire extension: the new end date and why. It creates an addendum contract to be signed; it does not change the deployment. */
public record ExtensionRequest(@NotNull LocalDate newEndDate, String reason) {}
