package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** An extra fixed charge on a client's price list: added to every fee note, or once a month, or once a year. */
@Entity
@Table(name = "pay_client_charges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayClientCharge {

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "pay_client_id", nullable = false) private UUID payClientId;
    @Column(nullable = false) private String description;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private String billing;
    @Column(nullable = false) private String status = "ACTIVE";
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Version private Long version;

    public static PayClientCharge create(UUID tenantId, UUID clientId, String description, BigDecimal amount, String billing) {
        PayClientCharge c = new PayClientCharge();
        c.tenantId = tenantId; c.payClientId = clientId; c.description = description.trim(); c.amount = amount; c.billing = billing;
        return c;
    }

    public void archive() { this.status = "ARCHIVED"; }
}
