package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Request and response shapes for the fuel customer portal. Nothing here carries cost, margin, tanks, drivers or internal notes. */
public final class PortalDtos {
    private PortalDtos() {}

    public record AuthResponse(String token, UUID userId, String email, String fullName) {}
    public record RegisterRequest(@NotBlank String inviteToken, @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password, @NotBlank String fullName) {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record InviteRequest(@NotBlank @Email String email) {}
    public record GrantResponse(UUID id, String inviteEmail, String status, Instant invitedAt, Instant acceptedAt, Instant revokedAt) {}

    public record AccountSummary(UUID accountId, String customerName, int paymentTermsDays) {}
    public record SiteSummary(UUID id, String name) {}

    public record OrderRequestBody(
            UUID siteId,
            @NotBlank @Size(max = 30) String fuelType,
            @NotNull @Positive @DecimalMax(value = "500000", message = "For more than 500,000 litres please contact us") BigDecimal litres,
            LocalDate requestedDate,
            @Size(max = 60) String customerReference,
            @Size(max = 300) String notes
    ) {}

    public record OrderView(UUID id, String orderNumber, String siteName, String fuelType, BigDecimal litresOrdered, BigDecimal pricePerLitre,
                            BigDecimal value, String status, LocalDate requestedDate, String customerReference,
                            BigDecimal litresDelivered, BigDecimal litresRemaining, Instant createdAt) {}

    public record DeliveryView(UUID id, Instant deliveredAt, String orderNumber, String siteName, String fuelType, BigDecimal litres,
                               BigDecimal pricePerLitre, BigDecimal total, String receiptNumber) {}
}
