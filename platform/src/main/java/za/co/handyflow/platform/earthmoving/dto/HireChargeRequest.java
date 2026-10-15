package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One billing period of a deployment. {@code vatRate} is a percentage and defaults to 15. {@code unitRate} is optional: leave it
 * out to charge the machine's own rate; give it, with a reason, to agree a different rate for this charge only.
 */
public record HireChargeRequest(@NotNull UUID deploymentId, @NotNull String basis, @NotNull LocalDate periodFrom,
                                @NotNull LocalDate periodTo, BigDecimal vatRate,
                                BigDecimal unitRate, String rateReason) {}
