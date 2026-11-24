package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One person or entity in one role for a client: a director, a shareholder, a beneficial owner and so on. */
@Entity
@Table(name = "compliance_client_parties")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceClientParty {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "party_type", nullable = false)
    private String partyType = "PERSON";

    @Column(name = "id_type")
    private String idType;

    @Column(name = "id_number")
    private String idNumber;

    @Column(nullable = false)
    private String role;

    @Column(name = "ownership_percent")
    private BigDecimal ownershipPercent;

    @Column(name = "appointed_on")
    private LocalDate appointedOn;

    @Column(name = "resigned_on")
    private LocalDate resignedOn;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    public record Details(String fullName, String partyType, String idType, String idNumber, String role,
                          BigDecimal ownershipPercent, LocalDate appointedOn, LocalDate resignedOn, String notes) {}

    public static ComplianceClientParty create(TenantId tenantId, UUID clientId, Details d, UUID by) {
        ComplianceClientParty p = new ComplianceClientParty();
        p.tenantId = tenantId;
        p.clientId = clientId;
        p.createdAt = Instant.now();
        p.createdBy = by;
        p.apply(d, by);
        return p;
    }

    public void apply(Details d, UUID by) {
        String name = ClientProfileRules.clean(d.fullName(), 255);
        if (name == null) throw new IllegalArgumentException("fullName is required");
        String type = ClientProfileRules.requireOneOf("partyType", d.partyType() == null ? "PERSON" : d.partyType(), ClientPartyRules.PARTY_TYPES);
        String role = ClientProfileRules.requireOneOf("role", d.role(), ClientPartyRules.ROLES);
        if (role == null) throw new IllegalArgumentException("role is required");
        String idType = ClientProfileRules.requireOneOf("idType", d.idType(), ClientPartyRules.ID_TYPES);
        String idNumber = ClientPartyRules.idNumber(idType, d.idNumber());
        BigDecimal pct = ClientPartyRules.percent(role, d.ownershipPercent());
        ClientPartyRules.dates(d.appointedOn(), d.resignedOn());
        this.fullName = name;
        this.partyType = type;
        this.idType = idType;
        this.idNumber = idNumber;
        this.role = role;
        this.ownershipPercent = pct;
        this.appointedOn = d.appointedOn();
        this.resignedOn = d.resignedOn();
        this.notes = ClientProfileRules.clean(d.notes(), 2000);
        this.updatedAt = Instant.now();
        this.updatedBy = by;
    }

    public boolean isActive() { return resignedOn == null; }

    public void resign(LocalDate on, UUID by) {
        if (resignedOn != null) throw new IllegalStateException("Already resigned");
        LocalDate date = on == null ? LocalDate.now() : on;
        ClientPartyRules.dates(appointedOn, date);
        this.resignedOn = date;
        this.updatedAt = Instant.now();
        this.updatedBy = by;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
