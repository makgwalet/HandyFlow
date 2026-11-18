package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Category tolerances, supplier VAT overrides, exchange rates and the contract-savings report. */
public final class PolicyRefinementDtos {

    private PolicyRefinementDtos() {}

    public record CategoryTolerance(UUID categoryId, String categoryName, BigDecimal tolerancePct, String updatedByName, Instant updatedAt) {}

    public record CategoryOption(UUID id, String name) {}

    public record SupplierVat(UUID supplierId, String supplierName, BigDecimal vatRecoverablePct, String note, String updatedByName, Instant updatedAt) {}

    public record SupplierOption(UUID id, String name) {}

    public record ExchangeRate(UUID id, String currency, BigDecimal rate, LocalDate effectiveFrom, String createdByName, Instant createdAt) {}

    public record Overview(String baseCurrency, BigDecimal companyTolerancePct, BigDecimal companyVatRecoverablePct,
                           List<CategoryTolerance> categoryTolerances, List<CategoryOption> categories,
                           List<SupplierVat> supplierVat, List<SupplierOption> suppliers,
                           List<ExchangeRate> exchangeRates, List<String> currenciesWithoutRate) {}

    public record ToleranceRequest(BigDecimal tolerancePct) {}

    public record SupplierVatRequest(BigDecimal vatRecoverablePct, String note) {}

    public record RateRequest(String currency, BigDecimal rate, LocalDate effectiveFrom) {}

    /** restated / skipped: how many budget commitments were recomputed, and how many were left because their currency has no rate. */
    public record Change(Overview overview, int commitmentsRestated, int commitmentsSkipped) {}

    public record SavingsItem(UUID catalogueItemId, String itemName, BigDecimal qty, BigDecimal listValue, BigDecimal paidValue,
                              BigDecimal saving, BigDecimal leakage, int lines, int contractLines) {}

    public record SavingsReport(LocalDate from, LocalDate to, String baseCurrency, BigDecimal listValue, BigDecimal paidValue,
                                BigDecimal saving, BigDecimal leakage, int lines, int linesWithoutListPrice, int linesWithoutRate,
                                List<SavingsItem> byItem) {}
}
