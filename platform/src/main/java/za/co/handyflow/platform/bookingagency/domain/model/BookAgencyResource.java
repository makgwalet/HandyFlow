package za.co.handyflow.platform.bookingagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A bookable resource at a client's business — a staff member, chair,
 * room, or similar — the "who/what performs it" half of the booking
 * model, mirroring bookings.Staff's role for the agency's client-facing
 * scheduling.
 * <p>
 * WORKING HOURS: deliberately simple — one start/end time pair applied
 * Monday-Friday, no per-day overrides, no recurring exception dates
 * (public holidays, staff leave). bookings.AvailabilityTab's own
 * "quick preset blocks" already handle that richer case for internal
 * scheduling; this module starts simpler and can grow into that same
 * shape later if agency clients' actual businesses need it — not
 * pre-built speculatively now.
 */
@Entity
@Table(name = "booka_resources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookAgencyResource {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId; // the AGENCY's tenant

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "role_description")
    private String roleDescription; // e.g. "Stylist", "Consulting Room 2"

    @Column(name = "working_hours_start")
    private LocalTime workingHoursStart; // null = use client-level default, if one is ever added

    @Column(name = "working_hours_end")
    private LocalTime workingHoursEnd;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static BookAgencyResource create(UUID tenantId, UUID clientId, String name,
                                            String roleDescription, LocalTime workingHoursStart,
                                            LocalTime workingHoursEnd) {
        BookAgencyResource r = new BookAgencyResource();
        r.tenantId = tenantId;
        r.clientId = clientId;
        r.name = name;
        r.roleDescription = roleDescription;
        r.workingHoursStart = workingHoursStart != null ? workingHoursStart : LocalTime.of(9, 0);
        r.workingHoursEnd = workingHoursEnd != null ? workingHoursEnd : LocalTime.of(17, 0);
        r.active = true;
        r.createdAt = Instant.now();
        r.updatedAt = Instant.now();
        return r;
    }

    public void update(String name, String roleDescription, LocalTime workingHoursStart, LocalTime workingHoursEnd) {
        this.name = name;
        this.roleDescription = roleDescription;
        this.workingHoursStart = workingHoursStart;
        this.workingHoursEnd = workingHoursEnd;
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