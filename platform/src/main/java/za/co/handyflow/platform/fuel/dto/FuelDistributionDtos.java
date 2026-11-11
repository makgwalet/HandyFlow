package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Phase 3 distribution controls: delivery variance review and proof-of-delivery evidence. */
public final class FuelDistributionDtos {
    private FuelDistributionDtos() {}

    public record VarianceView(UUID id, UUID deliveryId, String receiptNumber, String driverName, String vehicleReg, BigDecimal loadedLitres,
                               BigDecimal deliveredLitres, BigDecimal varianceLitres, BigDecimal variancePct, BigDecimal tolerancePct,
                               String status, String decision, String note, UUID reviewedBy, Instant reviewedAt, Instant detectedAt) {}

    public record VarianceList(boolean toleranceSet, java.util.List<VarianceView> variances) {}

    public record NoteRequest(@NotBlank @Size(min = 5, max = 300, message = "Note must be 5 to 300 characters") String note) {}
    public record CloseVarianceRequest(@NotBlank String decision, @NotBlank @Size(min = 5, max = 300, message = "Note must be 5 to 300 characters") String note) {}

    public record EvidenceRequest(@NotBlank String kind, @Size(max = 500) String fileRef, BigDecimal latitude, BigDecimal longitude,
                                  BigDecimal accuracyM, @Size(max = 300) String note) {}
    public record SupersedeRequest(@NotBlank @Size(min = 5, max = 300, message = "Reason must be 5 to 300 characters") String reason, EvidenceRequest replacement) {}

    public record EvidenceView(UUID id, UUID deliveryId, String kind, String fileRef, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyM,
                               String note, Instant capturedAt, UUID capturedBy, String status, String supersededReason, UUID verifiedBy,
                               Instant verifiedAt, boolean locked) {}
}
