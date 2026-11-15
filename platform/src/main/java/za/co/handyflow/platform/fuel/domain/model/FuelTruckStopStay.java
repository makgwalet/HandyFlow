package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Phase 7d: an overnight stay at the truck stop. The rate is the one in force when the stay was booked. A night is counted for each
 * date from arrival up to, not including, departure, with a minimum of one night. Nothing is invoiced here.
 */
@Entity
@Table(name = "fuel_truck_stop_stays")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelTruckStopStay {

    public static final String BOOKED = "BOOKED", CHECKED_IN = "CHECKED_IN", CHECKED_OUT = "CHECKED_OUT", CANCELLED = "CANCELLED", NO_SHOW = "NO_SHOW";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_registration", nullable = false, length = 20)
    private String vehicleRegistration;

    @Column(name = "driver_name", length = 120)
    private String driverName;

    @Column(name = "driver_phone", length = 40)
    private String driverPhone;

    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(name = "actual_departure_date")
    private LocalDate actualDepartureDate;

    @Column(nullable = false, length = 12)
    private String status = BOOKED;

    @Column(name = "rate_per_night", nullable = false)
    private BigDecimal ratePerNight;

    @Column(length = 300)
    private String note;

    @Column(name = "cancelled_reason", length = 300)
    private String cancelledReason;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Column(name = "account_id")
    private UUID accountId;

    public static final String PENDING = "PENDING", CLAIMED = "CLAIMED", INVOICED = "INVOICED", FAILED = "FAILED", LINK_FAILED = "LINK_FAILED";

    /** Null for a stay paid at the gate. Otherwise PENDING, CLAIMED, INVOICED, FAILED or LINK_FAILED. The invoice columns are changed by guarded updates, not by this entity. */
    @Column(name = "invoice_state", length = 12, insertable = false, updatable = false)
    private String invoiceState;

    @Column(name = "invoice_id", insertable = false, updatable = false)
    private UUID invoiceId;

    @Column(name = "invoice_number", length = 40, insertable = false, updatable = false)
    private String invoiceNumber;

    @Column(name = "invoice_error", length = 500, insertable = false, updatable = false)
    private String invoiceError;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    public static FuelTruckStopStay book(TenantId tenantId, String registration, String driverName, String driverPhone, LocalDate arrival, LocalDate departure,
                                         BigDecimal ratePerNight, String note, UUID by) {
        if (registration == null || registration.isBlank()) throw new IllegalArgumentException("Give the truck's registration.");
        if (registration.trim().length() > 20) throw new IllegalArgumentException("The registration can be at most 20 characters.");
        if (arrival == null || departure == null) throw new IllegalArgumentException("Give the arrival and departure dates.");
        if (!departure.isAfter(arrival)) throw new IllegalArgumentException("The departure must be after the arrival; an overnight stay is at least one night.");
        if (ratePerNight == null || ratePerNight.signum() < 0) throw new IllegalArgumentException("Set the nightly rate first.");
        FuelTruckStopStay s = new FuelTruckStopStay();
        s.tenantId = tenantId; s.vehicleRegistration = registration.trim().toUpperCase();
        s.driverName = clean(driverName, 120); s.driverPhone = clean(driverPhone, 40); s.note = clean(note, 300);
        s.arrivalDate = arrival; s.departureDate = departure; s.ratePerNight = ratePerNight; s.createdBy = by;
        return s;
    }

    /** Charges the stay to a customer account; only while it has not ended. */
    public void chargeTo(UUID accountId) {
        requireStatus("change the account of", BOOKED, CHECKED_IN);
        this.accountId = accountId; this.updatedAt = Instant.now();
    }

    private static String clean(String v, int max) { return v == null || v.isBlank() ? null : (v.trim().length() > max ? v.trim().substring(0, max) : v.trim()); }

    public void checkIn() {
        requireStatus("check in", BOOKED);
        this.status = CHECKED_IN; this.checkedInAt = Instant.now(); this.updatedAt = this.checkedInAt;
    }

    /** Leaves on {@code date} (not before the arrival date). The nights charged run to that date, at least one. */
    public void checkOut(LocalDate date) {
        requireStatus("check out", CHECKED_IN);
        if (date == null) throw new IllegalArgumentException("Give the departure date.");
        if (date.isBefore(arrivalDate)) throw new IllegalArgumentException("The departure cannot be before the arrival.");
        this.status = CHECKED_OUT; this.actualDepartureDate = date; this.checkedOutAt = Instant.now(); this.updatedAt = this.checkedOutAt;
    }

    /** Moves the planned departure of a stay that has not ended. */
    public void changeDeparture(LocalDate departure) {
        requireStatus("change the departure of", BOOKED, CHECKED_IN);
        if (departure == null || !departure.isAfter(arrivalDate)) throw new IllegalArgumentException("The departure must be after the arrival.");
        this.departureDate = departure; this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        requireStatus("cancel", BOOKED);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give a reason for cancelling the stay.");
        this.status = CANCELLED; this.cancelledReason = clean(reason, 300); this.updatedAt = Instant.now();
    }

    /** The truck did not arrive. Only a booked stay whose arrival date has come can be marked. */
    public void noShow(LocalDate today) {
        requireStatus("mark as no-show", BOOKED);
        if (today == null || today.isBefore(arrivalDate)) throw new IllegalStateException("The truck is not due yet.");
        this.status = NO_SHOW; this.cancelledReason = "Did not arrive"; this.updatedAt = Instant.now();
    }

    /** The date the stay ends for counting nights: the actual departure once out, otherwise the planned one. */
    public LocalDate endDate() { return actualDepartureDate != null ? actualDepartureDate : departureDate; }

    /** Nights from arrival to the end date, at least one. */
    public int nights() { return (int) Math.max(1, ChronoUnit.DAYS.between(arrivalDate, endDate())); }

    public BigDecimal amount() { return ratePerNight.multiply(BigDecimal.valueOf(nights())).setScale(2, java.math.RoundingMode.HALF_UP); }

    /** Still in the yard after the planned departure date. */
    public boolean isOverstaying(LocalDate today) { return CHECKED_IN.equals(status) && today.isAfter(departureDate); }

    /** True while the stay holds a bay: booked or in the yard. */
    public boolean holdsBay() { return BOOKED.equals(status) || CHECKED_IN.equals(status); }

    private void requireStatus(String what, String... allowed) {
        for (String a : allowed) if (a.equals(status)) return;
        throw new IllegalStateException("Cannot " + what + " a stay that is " + status.toLowerCase().replace('_', ' ') + ".");
    }
}
