package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProfitDtos {

    private ProfitDtos() {}

    public record CostRateRequest(@NotNull UUID userId, @NotNull BigDecimal hourlyCost, @NotNull LocalDate effectiveFrom) {}

    public record CostRateView(UUID id, UUID userId, BigDecimal hourlyCost, LocalDate effectiveFrom, UUID setBy, Instant setAt) {}

    /** costComplete is false when some of the client's hours had no cost rate yet, so the cost and margin are understated. */
    public record ClientLine(UUID clientId, String clientName, int minutes, int billableMinutes, int reworkMinutes, int unplannedMinutes, int unratedMinutes, boolean costComplete,
                             BigDecimal labourCost, BigDecimal invoiced, BigDecimal collected, BigDecimal margin, BigDecimal marginPct) {}

    public record ServiceLine(String serviceCode, int minutes, int unratedMinutes, BigDecimal labourCost) {}

    public record PersonLine(UUID userId, int minutes, int billableMinutes, int unratedMinutes, BigDecimal utilisationPct, BigDecimal labourCost) {}

    public record Report(LocalDate from, LocalDate to, int minutes, int unratedMinutes, BigDecimal labourCost, BigDecimal invoiced, BigDecimal collected,
                         List<ClientLine> clients, List<ServiceLine> services, List<PersonLine> people, String basis) {}
}
