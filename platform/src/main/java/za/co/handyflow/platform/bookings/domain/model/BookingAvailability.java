package za.co.handyflow.platform.bookings.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "booking_availability")
@Getter
@NoArgsConstructor
public class BookingAvailability {

    @Id UUID id;
    @Column(name = "tenant_id")   UUID tenantId;
    @Column(name = "staff_id")    UUID staffId;
    @Column(name = "day_of_week") int dayOfWeek;  // 0=Sun, 1=Mon … 6=Sat
    @Column(name = "start_time")  LocalTime startTime;
    @Column(name = "end_time")    LocalTime endTime;
    boolean active = true;

    public static BookingAvailability create(TenantId tenantId, UUID staffId,
                                             int dayOfWeek, LocalTime startTime,
                                             LocalTime endTime) {
        BookingAvailability a = new BookingAvailability();
        a.id          = UUID.randomUUID();
        a.tenantId    = tenantId.getValue();
        a.staffId     = staffId;
        a.dayOfWeek   = dayOfWeek;
        a.startTime   = startTime;
        a.endTime     = endTime;
        a.active      = true;
        return a;
    }
}