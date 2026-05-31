package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_ticket_tiers")
@Getter
@NoArgsConstructor
public class EventTicketTier {

    @Id UUID id;
    @Column(name = "tenant_id")          UUID tenantId;
    @Column(name = "event_id")           UUID eventId;
    String name;
    String description;
    BigDecimal price;
    String currency = "ZAR";
    int quantity;
    @Column(name = "quantity_sold")       int quantitySold;
    @Column(name = "quantity_checked_in") int quantityCheckedIn;
    @Column(name = "sale_start")         LocalDateTime saleStart;
    @Column(name = "sale_end")           LocalDateTime saleEnd;
    boolean active = true;
    @Column(name = "created_at")         Instant createdAt;
    @Column(name = "updated_at")         Instant updatedAt;

    public static EventTicketTier create(TenantId tenantId, UUID eventId,
                                         String name, String description,
                                         BigDecimal price, int quantity,
                                         LocalDateTime saleStart,
                                         LocalDateTime saleEnd) {
        EventTicketTier t = new EventTicketTier();
        t.id          = UUID.randomUUID();
        t.tenantId    = tenantId.getValue();
        t.eventId     = eventId;
        t.name        = name;
        t.description = description;
        t.price       = price != null ? price : BigDecimal.ZERO;
        t.currency    = "ZAR";
        t.quantity    = quantity;
        t.quantitySold = 0;
        t.quantityCheckedIn = 0;
        t.saleStart   = saleStart;
        t.saleEnd     = saleEnd;
        t.active      = true;
        t.createdAt   = Instant.now();
        t.updatedAt   = Instant.now();
        return t;
    }

    public boolean isAvailable() {
        return active && quantitySold < quantity;
    }

    public int getAvailable() {
        return Math.max(0, quantity - quantitySold);
    }

    public void incrementSold() {
        this.quantitySold++;
        this.updatedAt = Instant.now();
    }

    public void incrementCheckedIn() {
        this.quantityCheckedIn++;
        this.updatedAt = Instant.now();
    }
}