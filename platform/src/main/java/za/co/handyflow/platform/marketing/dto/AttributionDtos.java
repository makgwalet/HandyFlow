package za.co.handyflow.platform.marketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AttributionDtos {
    private AttributionDtos() {}

    public record Totals(long revenueCents, long orders, long attributedRevenueCents, long attributedOrders) {}

    public record CampaignRevenue(UUID id, String name, String kind, long sent, long clicked, long orders, long revenueCents,
                                  Long revenuePerSentCents, Long costCents, Double roi) {}

    public record DayRevenue(String date, long revenueCents, long attributedCents) {}

    public record Overview(int days, String currency, int windowDays, Totals totals, long otherCurrencyCount,
                           List<CampaignRevenue> campaigns, List<DayRevenue> series) {}

    public record ConversionRow(UUID id, String kind, String emailMasked, long amountCents, String currency,
                                Instant paidAt, String description, String source, Instant clickAt) {}

    public record RecordPaymentRequest(
            @NotBlank @Size(max = 320) String email,
            @Positive long amountCents,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @Size(max = 100) String externalRef,
            Instant paidAt,
            @Size(max = 200) String description) {}

    public record RecordRefundRequest(
            @NotBlank @Size(max = 100) String originalRef,
            @Size(max = 100) String externalRef,
            @Positive long amountCents,
            Instant refundedAt) {}

    public record RecordResult(boolean recorded, boolean attributed, UUID campaignId, String campaignName) {}

    /** costCents null clears the cost. */
    public record CostRequest(@PositiveOrZero Long costCents) {}

    public record AttributionSettings(int windowDays) {}
}