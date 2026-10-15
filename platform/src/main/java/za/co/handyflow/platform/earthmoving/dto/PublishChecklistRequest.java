package za.co.handyflow.platform.earthmoving.dto;

import java.time.LocalDate;

/** {@code effectiveFrom} is today when blank; it cannot be in the past. */
public record PublishChecklistRequest(LocalDate effectiveFrom) {}
