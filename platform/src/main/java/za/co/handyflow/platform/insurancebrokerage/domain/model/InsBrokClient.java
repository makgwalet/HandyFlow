package za.co.handyflow.platform.insurancebrokerage.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A business or individual the brokerage places and manages cover for.
 * Plain-entity, raw-UUID provider-module convention (see package-info),
 * same shape as {@code CollAgencyClient}/{@code RecAgencyClient}.
 * <p>
 * {@code clientType} covers the "personal &amp; commercial" short-term
 * lines confirmed in scope (§1 of the module status doc) —
 * INDIVIDUAL for personal lines clients, COMMERCIAL for business
 * clients — distinct from {@code InsBrokPolicy.lineOfBusiness}, which
 * describes what is being insured (MOTOR/PROPERTY/etc.), not who the
 * client is.
 * <p>
 * {@code defaultCommissionRatePct} is the fallback rate used by
 * {@code InsBrokPolicy}/{@code InsBrokCommissionInvoiceService} when a
 * policy carries no rate of its own — same "client default, policy can
 * override" shape {@code CollAgencyClient.commissionRatePct} already
 * established, and same revenue-critical "must be set before commission
 * can post, never silently defaulted" guard
 * {@code CollAgencyTrustTransactionService.resolveCommissionRate()}
 * already enforces.
 */
@Entity
@Table(name = "insbrok_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsBrokClient {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "client_type", nullable = false)
    private String clientType; // INDIVIDUAL | COMMERCIAL

    @Column(name = "registration_or_id_number")
    private String registrationOrIdNumber;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "default_commission_rate_pct", precision = 5, scale = 2)
    private BigDecimal defaultCommissionRatePct;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static InsBrokClient create(UUID tenantId, String clientName, String clientType,
                                        String registrationOrIdNumber, String contactName, String contactEmail,
                                        String contactPhone, String address,
                                        BigDecimal defaultCommissionRatePct, String notes) {
        InsBrokClient c = new InsBrokClient();
        c.tenantId = tenantId;
        c.clientName = clientName;
        c.clientType = clientType;
        c.registrationOrIdNumber = registrationOrIdNumber;
        c.contactName = contactName;
        c.contactEmail = contactEmail;
        c.contactPhone = contactPhone;
        c.address = address;
        c.defaultCommissionRatePct = defaultCommissionRatePct;
        c.notes = notes;
        Instant now = Instant.now();
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    public void update(String clientName, String clientType, String registrationOrIdNumber, String contactName,
                        String contactEmail, String contactPhone, String address,
                        BigDecimal defaultCommissionRatePct, String notes) {
        this.clientName = clientName;
        this.clientType = clientType;
        this.registrationOrIdNumber = registrationOrIdNumber;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.defaultCommissionRatePct = defaultCommissionRatePct;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
