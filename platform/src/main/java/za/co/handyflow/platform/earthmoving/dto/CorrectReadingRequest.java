package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Fix a reading that was entered wrongly. The reason is mandatory and kept. */
public record CorrectReadingRequest(@NotNull BigDecimal reading, String reason) {}
