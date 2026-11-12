package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Phase 7a: fuel brought back on the truck and put into a tank. */
public final class LoadReturnDtos {
    private LoadReturnDtos() {}

    public record ReturnRequest(@NotNull UUID tankId, @NotNull BigDecimal litres, @Size(max = 500) String note, UUID clientRequestId) {}

    public record ReturnView(UUID id, UUID deliveryId, UUID tankId, String tankName, BigDecimal litres, String note, UUID recordedBy, Instant recordedAt) {}

    /** A load with litres declared as returned on a failed attempt and not yet all confirmed back into a tank. */
    public record Outstanding(UUID deliveryId, String customerName, String vehicleReg, String driverName, BigDecimal loadedLitres,
                              BigDecimal declaredReturned, BigDecimal confirmedBack, BigDecimal outstanding, Instant lastAttemptAt) {}
}
