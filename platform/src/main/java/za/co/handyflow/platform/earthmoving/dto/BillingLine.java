package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One deployment in a month's billing. {@code state} is READY (a charge can be raised), ALREADY_BILLED (the days are charged) or
 * EXCEPTION (it needs a person; {@code message} says why). The amounts are filled for READY lines.
 */
public record BillingLine(UUID deploymentId, UUID assetId, String machine, String customerName, String siteName, String basis,
                          LocalDate periodFrom, LocalDate periodTo, String state, String message,
                          BigDecimal quantity, BigDecimal unitRate, BigDecimal amount, BigDecimal total, String rateSource) {}
