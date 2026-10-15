package za.co.handyflow.platform.invoicing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A change to the company's VAT treatment from a date. {@code standardRate} is a percentage, for example 15.00; it is ignored (stored as 0) when the company is not registered. */
public record VatSettingRequest(boolean vatRegistered, BigDecimal standardRate, LocalDate effectiveFrom, String reason) {}
