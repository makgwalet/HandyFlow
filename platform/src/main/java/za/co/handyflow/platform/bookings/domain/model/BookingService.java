package za.co.handyflow.platform.bookings.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_services")
@Getter
@NoArgsConstructor
public class BookingService {

    @Id UUID id;
    @Column(name = "tenant_id")        UUID tenantId;
    String name;
    String description;
    @Column(name = "duration_minutes") int durationMinutes;
    BigDecimal price;
    String currency = "ZAR";
    String color    = "#0D9488";
    boolean active  = true;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;

    public static BookingService create(TenantId tenantId, String name,
                                        String description, int durationMinutes,
                                        BigDecimal price, String color) {
        BookingService s = new BookingService();
        s.id              = UUID.randomUUID();
        s.tenantId        = tenantId.getValue();
        s.name            = name;
        s.description     = description;
        s.durationMinutes = durationMinutes;
        s.price           = price != null ? price : BigDecimal.ZERO;
        s.color           = color != null ? color : "#0D9488";
        s.currency        = "ZAR";
        s.active          = true;
        s.createdAt       = Instant.now();
        s.updatedAt       = Instant.now();
        return s;
    }

    public void update(String name, String description,
                       int durationMinutes, BigDecimal price, String color) {
        this.name            = name;
        this.description     = description;
        this.durationMinutes = durationMinutes;
        this.price           = price;
        this.color           = color;
        this.updatedAt       = Instant.now();
    }

    public void deactivate() {
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.active    = false;
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}