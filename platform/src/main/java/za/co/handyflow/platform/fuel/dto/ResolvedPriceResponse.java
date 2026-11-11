package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** The price that applies, and why: CONTRACT (customer-specific) beats STANDARD. */
public record ResolvedPriceResponse(BigDecimal pricePerLitre, String source, UUID priceId, BigDecimal minLitres) {}
