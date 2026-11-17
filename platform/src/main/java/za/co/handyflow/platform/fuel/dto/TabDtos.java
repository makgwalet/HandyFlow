package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Truck stop driver tabs: a bill of the truck stop's Bookings services, settled at the till or charged to a customer account. */
public final class TabDtos {
    private TabDtos() {}

    public record OpenRequest(@NotBlank @Size(max = 20) String vehicleRegistration, @Size(max = 120) String driverName, UUID accountId, UUID fleetVehicleId) {}

    public record AccountRequest(UUID accountId) {}

    /** The price is never sent: it is read from Bookings when the line is added. */
    public record AddLineRequest(@NotNull UUID offeringId, @NotNull @Positive BigDecimal quantity) {}

    public record SettleRequest(@NotBlank String paymentMethod) {}

    public record VoidRequest(@NotBlank @Size(min = 5, max = 300) String reason) {}

    public record LineView(UUID id, UUID offeringId, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}

    public record TabView(UUID id, String vehicleRegistration, String driverName, UUID accountId, String status, String paymentMethod, Instant openedAt, Instant settledAt,
                          String voidReason, String invoiceState, String invoiceNumber, String invoiceError, List<LineView> lines, BigDecimal total, UUID fleetVehicleId) {}

    /** The company's VAT treatment today: prices are ex-VAT and Invoicing adds this rate once. Zero when not registered. */
    public record VatInfo(boolean registered, BigDecimal ratePercent) {}
}
