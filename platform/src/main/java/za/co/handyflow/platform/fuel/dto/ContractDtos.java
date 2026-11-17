package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Phase 7c (B06b): supply contracts that group and report. They hold no prices. */
public final class ContractDtos {
    private ContractDtos() {}

    public record CreateRequest(@NotNull UUID accountId, @NotBlank @Size(max = 40) String contractNumber, @NotBlank @Size(max = 200) String title,
                                @NotNull LocalDate startDate, @NotNull LocalDate endDate, @Size(max = 30) String fuelType, BigDecimal committedLitres,
                                @Size(max = 2000) String terms) {}

    public record UpdateRequest(@NotBlank @Size(max = 200) String title, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                @Size(max = 30) String fuelType, BigDecimal committedLitres, @Size(max = 2000) String terms) {}

    public record CancelRequest(@NotBlank @Size(max = 300) String reason) {}

    /** A linked price entry, echoed read-only from the price list. */
    /**
     * fromLitres is the contract volume from which this price applies (0 = no threshold). inEffect is true for the one price the volume delivered so far
     * selects, when the contract has a volume ladder and that price is live and approved.
     */
    public record LinkedPrice(UUID priceId, String fuelType, BigDecimal pricePerLitre, BigDecimal minLitres, LocalDate validFrom, LocalDate validTo,
                              String approvalStatus, boolean live, BigDecimal fromLitres, boolean inEffect) {
        /** Before volume pricing existed. */
        public LinkedPrice(UUID priceId, String fuelType, BigDecimal pricePerLitre, BigDecimal minLitres, LocalDate validFrom, LocalDate validTo,
                           String approvalStatus, boolean live) {
            this(priceId, fuelType, pricePerLitre, minLitres, validFrom, validTo, approvalStatus, live, BigDecimal.ZERO, false);
        }
    }

    /** fromLitres may be left out: no threshold. */
    public record LinkRequest(@jakarta.validation.constraints.PositiveOrZero BigDecimal fromLitres) {}

    /**
     * status is CANCELLED, UPCOMING, ACTIVE or EXPIRED. deliveredLitres counts the account's delivered loads (of the contract's fuel type, when it
     * names one) from the start date to today or the end date, whichever is earlier. percentOfCommitment and litresRemaining are null when no
     * commitment was set. daysLeft is the days to the end date while the contract is active, otherwise null.
     */
    public record ContractView(UUID id, UUID accountId, String customerName, String contractNumber, String title, LocalDate startDate, LocalDate endDate,
                               String fuelType, BigDecimal committedLitres, String terms, String status, String cancelledReason,
                               BigDecimal deliveredLitres, BigDecimal percentOfCommitment, BigDecimal litresRemaining, Long daysLeft,
                               int linkedPrices, List<LinkedPrice> prices, Instant createdAt) {}
}
