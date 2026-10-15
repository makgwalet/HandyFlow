package za.co.handyflow.platform.invoicing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What is in force today ({@code configured} is false until a company sets it), changes still to start, and past changes.
 * {@code vatNumberOnFile} says whether the company profile has a VAT number, which switching VAT on requires.
 */
public record VatSettingsOverview(boolean vatRegistered, BigDecimal ratePercent, boolean configured, LocalDate effectiveFrom,
                                  List<VatSettingResponse> upcoming, List<VatSettingResponse> history, boolean vatNumberOnFile, BigDecimal platformRate) {}
