package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A hire charge, or (id null, status PREVIEW) what one would be. Amounts are in rand; {@code amount} excludes VAT. {@code attention} is why a pending charge is held back from invoicing. */
public record HireChargeResponse(UUID id, UUID deploymentId, UUID assetId, String machine, UUID customerId, String customerName,
                                 String siteName, LocalDate periodFrom, LocalDate periodTo, String basis, BigDecimal quantity,
                                 BigDecimal unitRate, BigDecimal vatRate, BigDecimal amount, BigDecimal vatAmount, BigDecimal total,
                                 String description, String status, UUID invoiceId, String invoiceNumber, String note, Instant createdAt,
                                 String rateSource, BigDecimal includedKm, String attention, Instant recalculatedAt) {}
