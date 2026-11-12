package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PriceResponse(UUID id, String fuelType, UUID accountId, String customerName, BigDecimal pricePerLitre,
                            BigDecimal minLitres, LocalDate validFrom, LocalDate validTo, String note, boolean live,
                            String approvalStatus, String approvalReason, BigDecimal previousPricePerLitre, BigDecimal changePct,
                            BigDecimal costPerLitreAtEntry, BigDecimal marginPct) {}
