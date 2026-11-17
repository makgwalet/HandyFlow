package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A driver's bill at the truck stop. Lines are added while it is OPEN. Settling it at the till records how it was paid; a tab that names a customer
 * account is charged to that account instead. A settled tab is final. The invoice columns are changed by guarded updates, not by this entity.
 */
@Entity
@Table(name = "fuel_truck_stop_tabs")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelTruckStopTab {

    public static final String OPEN = "OPEN", SETTLED = "SETTLED", VOID = "VOID";
    public static final Set<String> TILL_METHODS = Set.of("CASH", "CARD", "OTHER");

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_registration", nullable = false, length = 20) private String vehicleRegistration;
    @Column(name = "driver_name", length = 120) private String driverName;
    @Column(name = "account_id") private UUID accountId;
    @Column(name = "fleet_vehicle_id") private UUID fleetVehicleId;
    @Column(nullable = false, length = 10) private String status = OPEN;
    @Column(name = "payment_method", length = 10) private String paymentMethod;
    @Column(name = "opened_by", updatable = false) private UUID openedBy;
    @Column(name = "opened_at", nullable = false, updatable = false) private Instant openedAt = Instant.now();
    @Column(name = "settled_by") private UUID settledBy;
    @Column(name = "settled_at") private Instant settledAt;
    @Column(name = "void_reason", length = 300) private String voidReason;

    @Column(name = "invoice_state", length = 12, insertable = false, updatable = false) private String invoiceState;
    @Column(name = "invoice_id", insertable = false, updatable = false) private UUID invoiceId;
    @Column(name = "invoice_number", length = 40, insertable = false, updatable = false) private String invoiceNumber;
    @Column(name = "invoice_error", length = 500, insertable = false, updatable = false) private String invoiceError;
    @Column(name = "invoice_claimed_at", insertable = false, updatable = false) private Instant invoiceClaimedAt;

    @Version private Long version;

    public static FuelTruckStopTab open(TenantId tenantId, String vehicleRegistration, String driverName, UUID accountId, UUID by) {
        String reg = vehicleRegistration == null ? "" : vehicleRegistration.trim().toUpperCase();
        if (reg.isEmpty()) throw new IllegalArgumentException("Enter the vehicle registration.");
        if (reg.length() > 20) throw new IllegalArgumentException("The registration is too long.");
        FuelTruckStopTab t = new FuelTruckStopTab();
        t.tenantId = tenantId; t.vehicleRegistration = reg; t.accountId = accountId; t.openedBy = by;
        t.driverName = driverName == null || driverName.isBlank() ? null : driverName.trim();
        return t;
    }

    /** Links to a Fleet vehicle; the registration becomes Fleet's. Only while open. */
    public void linkFleetVehicle(UUID vehicleId, String registration) {
        requireOpen();
        this.fleetVehicleId = vehicleId;
        if (vehicleId != null && registration != null && !registration.isBlank()) this.vehicleRegistration = registration.trim().toUpperCase();
    }

    public boolean isOpen() { return OPEN.equals(status); }

    public void requireOpen() {
        if (!isOpen()) throw new IllegalStateException("This tab is already " + status.toLowerCase() + ".");
    }

    /** Settles at the till. A tab that is charged to an account is not settled this way. */
    public void settleAtTill(String method, UUID by) {
        requireOpen();
        if (accountId != null) throw new IllegalStateException("This tab is charged to a customer account. Charge it to the account instead.");
        String m = method == null ? "" : method.trim().toUpperCase();
        if (!TILL_METHODS.contains(m)) throw new IllegalArgumentException("Choose how it was paid: cash, card or other.");
        status = SETTLED; paymentMethod = m; settledBy = by; settledAt = Instant.now();
    }

    public void chargeToAccount(UUID by) {
        requireOpen();
        if (accountId == null) throw new IllegalStateException("This tab has no customer account. Settle it at the till.");
        status = SETTLED; paymentMethod = "ACCOUNT"; settledBy = by; settledAt = Instant.now();
    }

    public void voidTab(String reason, UUID by) {
        requireOpen();
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        status = VOID; voidReason = reason.trim(); settledBy = by; settledAt = Instant.now();
    }

    /** Only while open: who the tab is charged to may change until it is settled. */
    public void setAccount(UUID accountId) {
        requireOpen();
        this.accountId = accountId;
    }
}
