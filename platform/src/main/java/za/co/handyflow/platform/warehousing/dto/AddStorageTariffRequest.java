package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** clientId null = the warehouse default; itemId needs a clientId. Starts on effectiveFrom, which cannot be in the past. */
public record AddStorageTariffRequest(UUID clientId, UUID itemId, @NotNull @DecimalMin("0") BigDecimal monthlyRate,
                                      @NotNull LocalDate effectiveFrom, @Size(max = 300) String note) {}
