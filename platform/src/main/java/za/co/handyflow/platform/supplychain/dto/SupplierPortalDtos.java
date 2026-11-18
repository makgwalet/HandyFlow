package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Everything the supplier portal sends or receives. Nothing here carries the buyer's estimates, weights or other suppliers' data. */
public final class SupplierPortalDtos {

    private SupplierPortalDtos() {}

    public record InviteRequest(String email) {}

    public record GrantRow(UUID id, UUID supplierId, String email, String status, Instant invitedAt, Instant acceptedAt, Instant inviteExpiresAt) {}

    public record RegisterRequest(String inviteToken, String password, String fullName) {}

    public record LoginRequest(String email, String password) {}

    public record AuthResponse(String token, UUID portalUserId, String email, String fullName) {}

    /** One RFQ this supplier was invited to. quotationStatus null = nothing submitted yet. */
    public record RfqSummary(UUID rfqId, String buyerName, String supplierName, String rfqNumber, String title, String status,
                             Instant closesAt, boolean openForQuotes, String currency, LocalDate requiredByDate,
                             String invitationStatus, String quotationStatus, BigDecimal myLandedCost, Instant myReceivedAt) {}

    public record RfqLine(UUID id, int lineNumber, String itemName, String description, BigDecimal quantity) {}

    public record RfqDetail(RfqSummary summary, String description, List<RfqLine> lines, QuotationResponse myQuotation,
                            String declineReason) {}

    /** A quotation from the portal: no supplierId, the supplier comes from the login. */
    public record QuoteRequest(String quoteReference, LocalDate validUntil, Integer leadTimeDays, Integer warrantyMonths,
                               Integer paymentTermsDays, BigDecimal deliveryCost, BigDecimal otherCharges, String notes,
                               List<QuotationRequest.Line> lines) {}

    public record ReasonRequest(String reason) {}
}
