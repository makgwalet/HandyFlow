package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sale, transfer or disposal (scrap / auction / salvage / donation) of a vehicle.
 * {@code bookValueAtDisposal} is a snapshot of {@code Vehicle.currentBookValue} at the
 * time, so the gain or loss on disposal stays fixed even if the vehicle's value is later edited.
 * Posting to the ledger is left to accounting — {@code accountingReference} records the
 * journal / asset-register reference once it has been done.
 */
@Entity
@Table(name = "fleet_vehicle_disposals")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleDisposal {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposal_method", length = 20)
    private DisposalMethod disposalMethod;

    @Column(name = "counterparty_name")
    private String counterpartyName;

    @Column(name = "counterparty_contact")
    private String counterpartyContact;

    @Column(name = "disposal_date", nullable = false)
    private LocalDate disposalDate;

    @Column(name = "sale_price", precision = 15, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "odometer_at_disposal")
    private Integer odometerAtDisposal;

    @Column(name = "book_value_at_disposal", precision = 15, scale = 2)
    private BigDecimal bookValueAtDisposal;

    @Column(name = "finance_cleared", nullable = false)
    private boolean financeCleared;

    @Column(name = "finance_settlement_amount", precision = 15, scale = 2)
    private BigDecimal financeSettlementAmount;

    @Column(name = "registration_transferred", nullable = false)
    private boolean registrationTransferred;

    @Column(name = "registration_transfer_date")
    private LocalDate registrationTransferDate;

    @Column(name = "accounting_reference", length = 100)
    private String accountingReference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public static VehicleDisposal create(TenantId tenantId, UUID vehicleId, UUID eventId,
                                         DisposalMethod method, String counterpartyName, String counterpartyContact,
                                         LocalDate disposalDate, BigDecimal salePrice, Integer odometerAtDisposal,
                                         BigDecimal bookValueAtDisposal, boolean financeCleared,
                                         BigDecimal financeSettlementAmount, boolean registrationTransferred,
                                         LocalDate registrationTransferDate, String accountingReference,
                                         String notes) {
        VehicleDisposal d = new VehicleDisposal();
        d.tenantId = tenantId;
        d.vehicleId = vehicleId;
        d.eventId = eventId;
        d.disposalMethod = method;
        d.counterpartyName = counterpartyName;
        d.counterpartyContact = counterpartyContact;
        d.disposalDate = disposalDate;
        d.salePrice = salePrice;
        d.odometerAtDisposal = odometerAtDisposal;
        d.bookValueAtDisposal = bookValueAtDisposal;
        d.financeCleared = financeCleared;
        d.financeSettlementAmount = financeSettlementAmount;
        d.registrationTransferred = registrationTransferred;
        d.registrationTransferDate = registrationTransferDate;
        d.accountingReference = accountingReference;
        d.notes = notes;
        return d;
    }

    /** Sale price minus book value; null unless both are known. Positive = gain, negative = loss. */
    public BigDecimal getGainLoss() {
        return salePrice != null && bookValueAtDisposal != null ? salePrice.subtract(bookValueAtDisposal) : null;
    }
}