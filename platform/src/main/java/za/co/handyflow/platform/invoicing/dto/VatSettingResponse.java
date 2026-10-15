package za.co.handyflow.platform.invoicing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VatSettingResponse(UUID id, boolean vatRegistered, BigDecimal standardRate, LocalDate effectiveFrom, String reason, Instant createdAt) {}
