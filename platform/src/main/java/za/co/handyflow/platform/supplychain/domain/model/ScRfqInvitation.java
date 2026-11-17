package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sc_rfq_invitations")
@Getter
@NoArgsConstructor
public class ScRfqInvitation {

    public static final String INVITED = "INVITED";
    public static final String RESPONDED = "RESPONDED";
    public static final String DECLINED = "DECLINED";

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "rfq_id", nullable = false) UUID rfqId;
    @Column(name = "supplier_id", nullable = false) UUID supplierId;
    @Column(name = "supplier_name", nullable = false, length = 255) String supplierName;
    @Column(name = "contact_email", length = 255) String contactEmail;
    @Column(nullable = false, length = 12) String status = INVITED;
    @Column(name = "decline_reason", length = 500) String declineReason;
    @Column(nullable = false) boolean emailed = false;
    @Column(name = "invited_at", nullable = false) Instant invitedAt;
    @Column(name = "responded_at") Instant respondedAt;

    public static ScRfqInvitation create(UUID tenantId, UUID rfqId, UUID supplierId, String supplierName, String email) {
        ScRfqInvitation i = new ScRfqInvitation();
        i.id = UUID.randomUUID();
        i.tenantId = tenantId;
        i.rfqId = rfqId;
        i.supplierId = supplierId;
        i.supplierName = supplierName;
        i.contactEmail = email;
        i.invitedAt = Instant.now();
        return i;
    }

    public void markEmailed() { this.emailed = true; }

    public void markResponded() {
        this.status = RESPONDED;
        this.respondedAt = Instant.now();
        this.declineReason = null;
    }

    public void decline(String reason) {
        this.status = DECLINED;
        this.declineReason = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        this.respondedAt = Instant.now();
    }
}