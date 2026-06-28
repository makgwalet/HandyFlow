package za.co.handyflow.platform.bookings.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * BookingServiceStaff — join entity for the booking_service_staff table.
 *
 * WHY a composite primary key as an @Embeddable?
 * The table PK is (service_id, staff_id) — a natural composite key.
 * JPA requires an @EmbeddedId or @IdClass for composite keys.
 * @EmbeddedId with an inner @Embeddable class is the cleaner approach:
 * it keeps the key fields grouped, Serializable (JPA cache requirement),
 * and gives you equals/hashCode via Lombok @EqualsAndHashCode.
 *
 * WHY no extra columns (e.g. skill level)?
 * The current requirement is binary: assigned or not.  When you need
 * skill levels ("junior", "senior") or effective dates, add them here —
 * the entity and repository are already the right place for that data.
 */
@Entity
@Table(name = "booking_service_staff")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BookingServiceStaff {

    @EmbeddedId
    private Id id;

    public UUID getServiceId() { return id.serviceId; }
    public UUID getStaffId()   { return id.staffId;   }

    public static BookingServiceStaff of(UUID serviceId, UUID staffId) {
        return new BookingServiceStaff(new Id(serviceId, staffId));
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {
        @Column(name = "service_id") private UUID serviceId;
        @Column(name = "staff_id")   private UUID staffId;
    }
}
