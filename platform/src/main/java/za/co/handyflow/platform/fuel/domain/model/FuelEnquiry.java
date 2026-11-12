package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** A customer's request for fuel before any quotation exists. It becomes QUOTED when a quotation is raised from it, or LOST/CANCELLED with a reason. */
@Entity
@Table(name = "fuel_enquiries")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelEnquiry {

    public static final Set<String> SOURCES = Set.of("PHONE", "EMAIL", "WALK_IN", "PORTAL", "OTHER");

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "enquiry_number", nullable = false, updatable = false, length = 20) private String enquiryNumber;
    @Column(name = "account_id") private UUID accountId;
    @Column(name = "contact_name", length = 120) private String contactName;
    @Column(name = "contact_phone", length = 40) private String contactPhone;
    @Column(name = "contact_email", length = 160) private String contactEmail;
    @Column(name = "fuel_type", nullable = false, length = 30) private String fuelType;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal litres;
    @Column(name = "requested_date") private LocalDate requestedDate;
    @Column(nullable = false, length = 10) private String source = "OTHER";
    @Column(nullable = false, length = 10) private String status = "NEW";
    @Column(name = "quote_order_id") private UUID quoteOrderId;
    @Column(name = "decision_reason", length = 300) private String decisionReason;
    @Column(length = 500) private String notes;
    @Column(name = "created_by", updatable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Version private Long version;

    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    public static FuelEnquiry create(TenantId tenantId, String number, UUID accountId, String contactName, String phone, String email,
                                     String fuelType, BigDecimal litres, LocalDate requestedDate, String source, String notes, UUID by) {
        FuelEnquiry e = new FuelEnquiry();
        e.tenantId = tenantId; e.enquiryNumber = number; e.createdBy = by;
        e.revise(accountId, contactName, phone, email, fuelType, litres, requestedDate, source, notes);
        return e;
    }

    /** Only an enquiry that is still NEW can be edited. */
    public void revise(UUID accountId, String contactName, String phone, String email, String fuelType, BigDecimal litres, LocalDate requestedDate, String source, String notes) {
        if (!isNew()) throw new IllegalStateException("This enquiry is already " + status.toLowerCase() + " and cannot be edited.");
        if (accountId == null && clean(contactName) == null) throw new IllegalArgumentException("Choose the customer account, or enter who is asking.");
        if (fuelType == null || fuelType.isBlank()) throw new IllegalArgumentException("Choose the fuel type.");
        if (litres == null || litres.signum() <= 0) throw new IllegalArgumentException("Litres must be greater than zero.");
        String src = source == null || source.isBlank() ? "OTHER" : source.trim().toUpperCase();
        if (!SOURCES.contains(src)) throw new IllegalArgumentException("Unknown source " + source + ".");
        this.accountId = accountId; this.contactName = clean(contactName); this.contactPhone = clean(phone); this.contactEmail = clean(email);
        this.fuelType = fuelType.trim(); this.litres = litres; this.requestedDate = requestedDate; this.source = src; this.notes = clean(notes);
        this.updatedAt = Instant.now();
    }

    public boolean isNew() { return "NEW".equals(status); }

    public void quoted(UUID orderId) {
        if (!isNew()) throw new IllegalStateException("This enquiry is already " + status.toLowerCase() + ".");
        if (orderId == null) throw new IllegalArgumentException("No quotation.");
        this.status = "QUOTED"; this.quoteOrderId = orderId; this.decidedAt = Instant.now(); this.updatedAt = this.decidedAt;
    }

    public void lost(String reason) { close("LOST", reason); }
    public void cancel(String reason) { close("CANCELLED", reason); }

    private void close(String to, String reason) {
        if (!isNew()) throw new IllegalStateException("This enquiry is already " + status.toLowerCase() + ".");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        this.status = to; this.decisionReason = reason.trim(); this.decidedAt = Instant.now(); this.updatedAt = this.decidedAt;
    }
}
