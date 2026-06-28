package za.co.handyflow.platform.bookings.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_staff")
@Getter
@NoArgsConstructor
public class BookingStaff {

    @Id UUID id;
    @Column(name = "tenant_id")   UUID tenantId;
    String name;
    String email;
    String phone;
    @Column(name = "employee_id") UUID employeeId;
    boolean active = true;
    @Column(name = "created_at")  Instant createdAt;
    @Column(name = "updated_at")  Instant updatedAt;

    public static BookingStaff create(TenantId tenantId, String name,
                                      String email, String phone,
                                      UUID employeeId) {
        BookingStaff s = new BookingStaff();
        s.id         = UUID.randomUUID();
        s.tenantId   = tenantId.getValue();
        s.name       = name;
        s.email      = email;
        s.phone      = phone;
        s.employeeId = employeeId;
        s.active     = true;
        s.createdAt  = Instant.now();
        s.updatedAt  = Instant.now();
        return s;
    }

    public void update(String name, String email, String phone) {
        this.name      = name;
        this.email     = email;
        this.phone     = phone;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active    = false;
        this.updatedAt = Instant.now();
    }
}