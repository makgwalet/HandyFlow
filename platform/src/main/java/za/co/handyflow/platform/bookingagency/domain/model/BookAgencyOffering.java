package za.co.handyflow.platform.bookingagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A bookable service type offered by a client's business — the "what's
 * being booked" half of the model, mirroring bookings.Service's role.
 * <p>
 * NAMED "Offering", NOT "Service" — a first draft of this class was
 * called BookAgencyService, one letter away from
 * bookingagency.application.internal.BookingAgencyService (the actual
 * @Service-annotated application service). That's a real, self-inflicted
 * confusion risk — easy to mistype or misread in an import — not just a
 * cosmetic naming preference. Caught and renamed before this was ever
 * wired into anything else, rather than left as a landmine for whoever
 * writes the next file that imports both classes.
 */
@Entity
@Table(name = "booka_offerings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookAgencyOffering {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes = 0; // gap enforced after this offering before the next booking

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static BookAgencyOffering create(UUID tenantId, UUID clientId, String name,
                                            int durationMinutes, int bufferMinutes, BigDecimal price) {
        BookAgencyOffering o = new BookAgencyOffering();
        o.tenantId = tenantId;
        o.clientId = clientId;
        o.name = name;
        o.durationMinutes = durationMinutes;
        o.bufferMinutes = bufferMinutes;
        o.price = price;
        o.active = true;
        o.createdAt = Instant.now();
        o.updatedAt = Instant.now();
        return o;
    }

    public void update(String name, int durationMinutes, int bufferMinutes, BigDecimal price) {
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.bufferMinutes = bufferMinutes;
        this.price = price;
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
}