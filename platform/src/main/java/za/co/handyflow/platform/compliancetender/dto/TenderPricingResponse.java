package za.co.handyflow.platform.compliancetender.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A tender's price schedule with its computed breakdown. {@code editable} is false once the tender has been submitted (the schedule is then the record of what was
 * priced). {@code configured} is false until someone has saved anything, in which case the settings shown are the defaults. estimatedValue is the tender's own
 * estimated value as entered; whether it includes VAT is not recorded, so the two totals are returned and no comparison is made.
 */
public record TenderPricingResponse(
        UUID tenderId, String tenderStatus, boolean editable, boolean configured, BigDecimal estimatedValue,
        Settings settings, List<LineResponse> lines, Breakdown breakdown
) {
    public record Settings(BigDecimal overheadPct, BigDecimal contingencyPct, BigDecimal profitPct,
                           boolean vatApplies, BigDecimal vatRatePct, String notes) {}

    public record LineResponse(UUID id, String section, String itemRef, String description, String unit,
                               BigDecimal quantity, BigDecimal unitCost, BigDecimal lineTotal, int sortOrder) {}

    public record SectionTotal(String section, int lineCount, BigDecimal subtotal) {}

    public record Breakdown(BigDecimal directCost, BigDecimal overhead, BigDecimal contingency, BigDecimal profit,
                            BigDecimal priceExVat, BigDecimal vat, BigDecimal priceInclVat,
                            BigDecimal marginPct, List<SectionTotal> sections) {}
}
