// fuel/domain/model/FuelDelivery.java

package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "fuel_deliveries")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelDelivery {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tank_id", nullable = false)
    private UUID tankId;

    @Column(name = "customer_id")
    private UUID customerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_address", columnDefinition = "jsonb")
    private Map<String, String> deliveryAddress;

    @Column(name = "fuel_type", nullable = false)
    private String fuelType;

    @Column(name = "litres_ordered", nullable = false, precision = 12, scale = 2)
    private BigDecimal litresOrdered;

    @Column(name = "litres_delivered", precision = 12, scale = 2)
    private BigDecimal litresDelivered;

    @Column(name = "price_per_litre", nullable = false, precision = 10, scale = 4)
    private BigDecimal pricePerLitre;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String status = "SCHEDULED";

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "vehicle_reg")
    private String vehicleReg;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(name = "receiver_id_badge")
    private String receiverIdBadge;

    @Column(name = "receiver_signature_url")
    private String receiverSignatureUrl;

    @Column(name = "meter_reading_start", precision = 12, scale = 2)
    private BigDecimal meterReadingStart;

    @Column(name = "meter_reading_end", precision = 12, scale = 2)
    private BigDecimal meterReadingEnd;

    @Column(name = "receipt_number")
    private String receiptNumber;

    @Column(name = "receipt_generated_at")
    private Instant receiptGeneratedAt;

    @Column(name = "signed_on_behalf", nullable = false)
    private boolean signedOnBehalf = false;

    @Column(name = "on_behalf_of")
    private String onBehalfOf;

    /**
     * FIX (notifications): idempotency guards for the scheduled reminder/overdue
     * sweeps — set the first time each fires so a delivery scheduled a week out
     * doesn't get re-reminded on every daily sweep, and an overdue delivery
     * doesn't re-alert every day it stays overdue. Mirrors
     * Task.overdueAlertSentAt's role in the Tasks module's scheduler.
     */
    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "overdue_alert_sent_at")
    private Instant overdueAlertSentAt;

    public static FuelDelivery create(TenantId tenantId, UUID tankId, UUID customerId,
                                      Map<String, String> deliveryAddress,
                                      String fuelType, BigDecimal litresOrdered,
                                      BigDecimal pricePerLitre, Instant scheduledAt,
                                      String driverName, String vehicleReg) {
        FuelDelivery d = new FuelDelivery();
        d.tenantId        = tenantId;
        d.tankId          = tankId;
        d.customerId      = customerId;
        d.deliveryAddress = deliveryAddress;
        d.fuelType        = fuelType.toUpperCase();
        d.litresOrdered   = litresOrdered;
        d.pricePerLitre   = pricePerLitre;
        d.totalAmount     = litresOrdered.multiply(pricePerLitre)
                .setScale(2, RoundingMode.HALF_UP);
        d.scheduledAt     = scheduledAt;
        d.driverName      = driverName;
        d.vehicleReg      = vehicleReg;
        d.status          = "SCHEDULED";
        d.createdAt       = Instant.now();
        d.updatedAt       = Instant.now();
        return d;
    }

    public void dispatch(String driverName, String vehicleReg) {
        this.status     = "IN_TRANSIT";
        this.driverName = driverName;
        this.vehicleReg = vehicleReg;
        this.updatedAt  = Instant.now();
    }

    public void complete(BigDecimal litresDelivered, String receiverName,
                         String receiverIdBadge, BigDecimal meterReadingStart,
                         BigDecimal meterReadingEnd, Boolean signedOnBehalf,
                         String onBehalfOf) {
        this.status              = "DELIVERED";
        this.litresDelivered     = litresDelivered;
        this.deliveredAt         = Instant.now();
        this.receiverName        = receiverName;
        this.receiverIdBadge     = receiverIdBadge;
        this.meterReadingStart   = meterReadingStart;
        this.meterReadingEnd     = meterReadingEnd;
        this.totalAmount         = litresDelivered.multiply(this.pricePerLitre)
                .setScale(2, RoundingMode.HALF_UP);
        this.updatedAt           = Instant.now();
        this.signedOnBehalf  = Boolean.TRUE.equals(signedOnBehalf);
        this.onBehalfOf      = onBehalfOf;
    }

    public void assignReceiptNumber(String receiptNumber) {
        this.receiptNumber        = receiptNumber;
        this.receiptGeneratedAt   = Instant.now();
        this.updatedAt            = Instant.now();
    }

    public void cancel() {
        this.status    = "CANCELLED";
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    /** Marks the upcoming-delivery reminder as sent. */
    public void markReminderSent() {
        this.reminderSentAt = Instant.now();
    }

    /** Marks the overdue alert as sent. */
    public void markOverdueAlertSent() {
        this.overdueAlertSentAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}