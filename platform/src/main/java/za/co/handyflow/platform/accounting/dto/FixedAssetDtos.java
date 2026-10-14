package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Fixed-asset register, depreciation and disposals (finance F25). */
public final class FixedAssetDtos {
    private FixedAssetDtos() {}

    public record Save(String name, String description, String serialNumber, String location,
                       UUID costAccountId, UUID accumulatedAccountId, UUID expenseAccountId, UUID costCentreId,
                       LocalDate acquiredOn, BigDecimal cost, BigDecimal residualValue,
                       String method, Integer lifeMonths, BigDecimal ratePercent) {}

    public record Asset(UUID id, String assetNumber, String name, String description, String serialNumber, String location,
                        UUID costAccountId, UUID accumulatedAccountId, UUID expenseAccountId, UUID costCentreId,
                        LocalDate acquiredOn, BigDecimal cost, BigDecimal residualValue, String method, Integer lifeMonths, BigDecimal ratePercent,
                        String status, LocalDate disposedOn, BigDecimal disposalProceeds,
                        BigDecimal accumulated, BigDecimal bookValue) {}

    public record PreviewLine(UUID assetId, String assetNumber, String name, BigDecimal charge, BigDecimal accumulatedAfter, BigDecimal bookValueAfter) {}
    public record Preview(LocalDate period, LocalDate lastRunPeriod, LocalDate nextPeriod, List<PreviewLine> lines, BigDecimal total, String problem) {}
    public record RunRequest(LocalDate period) {}
    public record Run(UUID id, LocalDate period, UUID journalEntryId, String journalNumber, String journalStatus, BigDecimal total, int assets, Instant createdAt) {}

    public record DisposeRequest(LocalDate disposedOn, BigDecimal proceeds, UUID proceedsAccountId, UUID gainLossAccountId) {}
    public record Disposal(UUID assetId, UUID journalEntryId, String journalNumber, BigDecimal bookValue, BigDecimal gainOrLoss) {}

    public record ReconRow(UUID accountId, String code, String name, String role, BigDecimal ledger, BigDecimal register, BigDecimal difference) {}
}
