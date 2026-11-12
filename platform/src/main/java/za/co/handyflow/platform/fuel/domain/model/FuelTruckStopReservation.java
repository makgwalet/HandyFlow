package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** The truck and driver behind a Bookings booking. The booking (time, resource, status, price) is Bookings' record, not copied here. */
@Entity
@Table(name = "fuel_truck_stop_reservations")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelTruckStopReservation {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "offering_id", nullable = false, updatable = false) private UUID offeringId;
    @Column(name = "booking_id", nullable = false, updatable = false) private UUID bookingId;
    @Column(name = "vehicle_registration", nullable = false, updatable = false, length = 20) private String vehicleRegistration;
    @Column(name = "driver_name", updatable = false, length = 120) private String driverName;
    @Column(name = "driver_phone", updatable = false, length = 40) private String driverPhone;
    @Column(name = "created_by", updatable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Version private Long version;

    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    public static FuelTruckStopReservation create(TenantId tenantId, UUID offeringId, UUID bookingId, String registration, String driverName, String driverPhone, UUID by) {
        String reg = clean(registration);
        if (reg == null) throw new IllegalArgumentException("Enter the vehicle registration.");
        if (reg.length() > 20) throw new IllegalArgumentException("The registration is too long.");
        FuelTruckStopReservation r = new FuelTruckStopReservation();
        r.tenantId = tenantId; r.offeringId = offeringId; r.bookingId = bookingId;
        r.vehicleRegistration = reg.toUpperCase(); r.driverName = clean(driverName); r.driverPhone = clean(driverPhone); r.createdBy = by;
        return r;
    }
}
