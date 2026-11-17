package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Driver self-service at the truck stop. Customer-facing shapes carry nothing about other customers: free bays are counts only, never trucks.
 * Staff shapes add who asked and for which account.
 */
public final class StayRequestDtos {
    private StayRequestDtos() {}

    public record StayRequestBody(@NotBlank @Size(max = 20) String vehicleRegistration, @Size(max = 120) String driverName, @Size(max = 40) String driverPhone,
                                  @NotNull LocalDate arrivalDate, @NotNull LocalDate departureDate, @Size(max = 300) String note) {}

    /** freeBays is null when the truck stop does not limit bays. */
    public record PortalNight(LocalDate date, Integer freeBays) {}

    /** nightlyRate is before VAT and null when the truck stop has not set one (requests are then not open). */
    public record PortalAvailability(BigDecimal nightlyRate, boolean open, List<PortalNight> nights) {}

    public record PortalStayRequestView(UUID id, String vehicleRegistration, String driverName, String driverPhone, LocalDate arrivalDate, LocalDate departureDate,
                                        int nights, String status, String note, String decisionNote, Instant createdAt) {}

    public record PortalStayView(UUID id, String vehicleRegistration, String driverName, LocalDate arrivalDate, LocalDate departureDate, int nights,
                                 String status, BigDecimal ratePerNight, BigDecimal amount, String invoiceNumber) {}

    public record StaffStayRequestView(UUID id, UUID accountId, String customerName, String requestedByEmail, String vehicleRegistration, String driverName,
                                       String driverPhone, LocalDate arrivalDate, LocalDate departureDate, int nights, String status, String note,
                                       String decisionNote, UUID stayId, Instant createdAt, Instant decidedAt) {}

    public record ApproveBody(@Size(max = 300) String note) {}

    public record DeclineBody(@NotBlank @Size(min = 5, max = 300) String reason) {}
}
