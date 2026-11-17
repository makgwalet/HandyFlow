package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** A customer's request for an overnight stay, made through the portal. Staff approve (booking the stay) or decline; the customer may cancel while it is pending. */
@Entity
@Table(name = "fuel_truck_stop_stay_requests")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelTruckStopStayRequest {

    public static final String PENDING = "PENDING", APPROVED = "APPROVED", DECLINED = "DECLINED", CANCELLED = "CANCELLED";
    public static final int MAX_NIGHTS = 30;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "account_id", nullable = false, updatable = false) private UUID accountId;
    @Column(name = "portal_user_id", nullable = false, updatable = false) private UUID portalUserId;
    @Column(name = "requested_by_email", updatable = false, length = 255) private String requestedByEmail;
    @Column(name = "vehicle_registration", nullable = false, updatable = false, length = 20) private String vehicleRegistration;
    @Column(name = "driver_name", updatable = false, length = 120) private String driverName;
    @Column(name = "driver_phone", updatable = false, length = 40) private String driverPhone;
    @Column(name = "arrival_date", nullable = false, updatable = false) private LocalDate arrivalDate;
    @Column(name = "departure_date", nullable = false, updatable = false) private LocalDate departureDate;
    @Column(length = 300, updatable = false) private String note;
    @Column(nullable = false, length = 10) private String status = PENDING;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_note", length = 300) private String decisionNote;
    @Column(name = "stay_id") private UUID stayId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    public static FuelTruckStopStayRequest create(TenantId tenantId, UUID accountId, UUID portalUserId, String email, String registration, String driverName,
                                                  String driverPhone, LocalDate arrival, LocalDate departure, String note, LocalDate today) {
        String reg = registration == null ? "" : registration.trim().toUpperCase(java.util.Locale.ROOT);
        if (reg.isEmpty()) throw new IllegalArgumentException("Give the vehicle registration.");
        if (reg.length() > 20) throw new IllegalArgumentException("The registration is too long.");
        if (arrival == null || departure == null) throw new IllegalArgumentException("Give the arrival and departure dates.");
        if (arrival.isBefore(today)) throw new IllegalArgumentException("The arrival cannot be in the past. For a truck already here, please call us.");
        if (!departure.isAfter(arrival)) throw new IllegalArgumentException("The departure must be after the arrival.");
        if (ChronoUnit.DAYS.between(arrival, departure) > MAX_NIGHTS) throw new IllegalArgumentException("A stay can be requested for up to " + MAX_NIGHTS + " nights. For longer, please call us.");
        FuelTruckStopStayRequest r = new FuelTruckStopStayRequest();
        r.tenantId = tenantId; r.accountId = accountId; r.portalUserId = portalUserId; r.requestedByEmail = email;
        r.vehicleRegistration = reg; r.driverName = clean(driverName, 120); r.driverPhone = clean(driverPhone, 40);
        r.arrivalDate = arrival; r.departureDate = departure; r.note = clean(note, 300);
        return r;
    }

    public boolean isPending() { return PENDING.equals(status); }
    public int nights() { return (int) ChronoUnit.DAYS.between(arrivalDate, departureDate); }

    public void approve(UUID by, UUID stayId, String note) {
        requirePending();
        this.status = APPROVED; this.decidedBy = by; this.decidedAt = Instant.now(); this.stayId = stayId; this.decisionNote = clean(note, 300);
    }

    public void decline(UUID by, String reason) {
        requirePending();
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters. The customer will see it.");
        this.status = DECLINED; this.decidedBy = by; this.decidedAt = Instant.now(); this.decisionNote = clean(reason, 300);
    }

    /** Only the customer's own portal user may cancel, and only while it is pending. */
    public void cancel(UUID portalUserId) {
        requirePending();
        if (!this.portalUserId.equals(portalUserId)) throw new IllegalStateException("Only the person who made this request can cancel it.");
        this.status = CANCELLED; this.decidedAt = Instant.now();
    }

    private void requirePending() { if (!isPending()) throw new IllegalStateException("This request has already been " + status.toLowerCase() + "."); }

    private static String clean(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
