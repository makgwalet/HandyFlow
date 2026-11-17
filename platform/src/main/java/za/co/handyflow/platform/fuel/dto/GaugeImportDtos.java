package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Tank-gauge feed: inbound readings held for review, and the labels a gauge system uses for tanks. */
public final class GaugeImportDtos {
    private GaugeImportDtos() {}

    /** One tank level. source and externalId make it idempotent; tank is the sender's label for the tank. */
    public record IncomingGauge(@NotBlank @Size(max = 40) String source, @NotBlank @Size(max = 100) String externalId, @NotBlank @Size(max = 60) String tank,
                                @NotNull @PositiveOrZero BigDecimal litres, @NotNull Instant readAt, @Size(max = 120) String senderName) {}

    public record GaugeView(UUID id, String source, String externalId, String status, Instant receivedAt, List<String> problems, String tankLabel, UUID tankId, String tankName,
                            BigDecimal litres, Instant readAt, String senderName, UUID decidedBy, Instant decidedAt, String decisionNote, UUID dipReadingId) {}

    public record AcceptRequest(@Size(max = 500) String note) {}

    public record RejectRequest(@NotBlank @Size(min = 5, max = 500) String reason) {}

    public record AliasRequest(@NotBlank @Size(max = 40) String source, @NotBlank @Size(max = 60) String alias, @NotNull UUID tankId) {}

    public record AliasView(UUID id, String source, String alias, UUID tankId, String tankName, Instant createdAt) {}
}
