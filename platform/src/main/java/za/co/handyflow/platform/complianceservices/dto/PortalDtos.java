package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PortalDtos {

    private PortalDtos() {}

    // ---- sign-in ----
    public record RegisterRequest(@NotBlank String inviteToken, @NotBlank @Size(min = 8, max = 200) String password, @NotBlank @Size(max = 255) String fullName) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record AuthResponse(String token, UUID userId, String email, String fullName) {}

    // ---- staff side: who has access to a client ----
    public record InviteRequest(@NotBlank @Size(max = 255) String email) {}

    public record GrantView(UUID id, UUID clientId, String email, String status, boolean emailSent, Instant invitedAt, Instant acceptedAt, Instant revokedAt, Instant inviteExpiresAt) {}

    // ---- what a client sees. Nothing here carries approver, cost, margin, staff names or internal notes ----
    public record ClientRef(UUID id, String name) {}

    public record QuoteLine(String serviceCode, String serviceName, String description, BigDecimal beforeDiscount, BigDecimal discount, BigDecimal professional,
                            BigDecimal disbursements, BigDecimal vat, BigDecimal total) {}

    public record Quote(UUID id, String quoteNumber, String title, String status, LocalDate validUntil, Instant sentAt, String currency, BigDecimal professional,
                        BigDecimal discountTotal, BigDecimal disbursements, BigDecimal vat, BigDecimal vatRatePct, BigDecimal total, String notes, String terms,
                        List<QuoteLine> lines) {}

    public record AgreementLine(String serviceCode, String serviceName, String description) {}

    public record Billing(LocalDate date, BigDecimal amount) {}

    public record Agreement(UUID id, String agreementNumber, String title, String status, LocalDate startDate, LocalDate endDate, String billingFrequency,
                            BigDecimal billingAmount, boolean autoRenew, int noticeDays, String terms, String currency, List<AgreementLine> scope, List<Billing> nextBills) {}

    public record Invoice(String invoiceNumber, String status, LocalDate billingDate, String description, BigDecimal total, BigDecimal paid, BigDecimal credited,
                          BigDecimal outstanding) {}

    public record Overview(ClientRef client, List<Quote> quotes, List<Agreement> agreements, List<Invoice> invoices, BigDecimal outstanding) {}
}
