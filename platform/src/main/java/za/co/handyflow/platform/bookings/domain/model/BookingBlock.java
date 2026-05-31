package za.co.handyflow.platform.bookings.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "booking_blocks")
@Getter
@NoArgsConstructor
public class BookingBlock {

    @Id UUID id;
    @Column(name = "tenant_id")  UUID tenantId;
    @Column(name = "staff_id")   UUID staffId;
    @Column(name = "block_date") LocalDate blockDate;
    @Column(name = "start_time") LocalTime startTime;
    @Column(name = "end_time")   LocalTime endTime;
    String reason;
    @Column(name = "created_at") Instant createdAt;

    public static BookingBlock create(TenantId tenantId, UUID staffId,
                                      LocalDate blockDate, LocalTime startTime,
                                      LocalTime endTime, String reason) {
        BookingBlock b = new BookingBlock();
        b.id        = UUID.randomUUID();
        b.tenantId  = tenantId.getValue();
        b.staffId   = staffId;
        b.blockDate = blockDate;
        b.startTime = startTime;
        b.endTime   = endTime;
        b.reason    = reason;
        b.createdAt = Instant.now();
        return b;
    }

    public boolean isFullDay() {
        return startTime == null;
    }

    // WHY? Check if a proposed slot overlaps this block
    public boolean overlaps(LocalTime slotStart, LocalTime slotEnd) {
        if (isFullDay()) return true;
        return slotStart.isBefore(endTime) && slotEnd.isAfter(startTime);
    }
}