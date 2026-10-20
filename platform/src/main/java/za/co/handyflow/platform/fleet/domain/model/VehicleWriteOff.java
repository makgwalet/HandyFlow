package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Write-off decision for a vehicle: assessment → decision → salvage → insurance settlement.
 * Recorded when the vehicle is declared a total loss; salvage and settlement details are
 * usually only known later, so they remain editable while the vehicle is WRITTEN_OFF.
 * The final asset disposal (salvage sold / scrapped) is a separate DISPOSAL event.
 */
@Entity
@Table(name = "fleet_vehicle_write_offs")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleWriteOff {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "accident_id")
    private UUID accidentId;

    @Column(name = "assessment_date")
    private LocalDate assessmentDate;

    @Column(name = "assessor_name")
    private String assessorName;

    @Column(name = "decision_date", nullable = false)
    private LocalDate decisionDate;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "pre_accident_value", precision = 15, scale = 2)
    private BigDecimal preAccidentValue;

    @Column(name = "salvage_value", precision = 15, scale = 2)
    private BigDecimal salvageValue;

    @Column(name = "salvage_buyer")
    private String salvageBuyer;

    @Column(name = "salvage_sold_at")
    private LocalDate salvageSoldAt;

    @Column(name = "insurance_settlement_amount", precision = 15, scale = 2)
    private BigDecimal insuranceSettlementAmount;

    @Column(name = "insurance_settled_at")
    private LocalDate insuranceSettledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static VehicleWriteOff create(TenantId tenantId, UUID vehicleId, UUID eventId, UUID accidentId,
                                         LocalDate assessmentDate, String assessorName, LocalDate decisionDate,
                                         String decisionNote, BigDecimal preAccidentValue, BigDecimal salvageValue,
                                         String salvageBuyer, LocalDate salvageSoldAt,
                                         BigDecimal insuranceSettlementAmount, LocalDate insuranceSettledAt) {
        VehicleWriteOff w = new VehicleWriteOff();
        w.tenantId = tenantId;
        w.vehicleId = vehicleId;
        w.eventId = eventId;
        w.accidentId = accidentId;
        w.createdAt = Instant.now();
        w.update(assessmentDate, assessorName, decisionDate, decisionNote, preAccidentValue, salvageValue,
                salvageBuyer, salvageSoldAt, insuranceSettlementAmount, insuranceSettledAt);
        return w;
    }

    /** Full replace of the assessment / salvage / settlement details. The accident link is fixed at creation. */
    public void update(LocalDate assessmentDate, String assessorName, LocalDate decisionDate, String decisionNote,
                       BigDecimal preAccidentValue, BigDecimal salvageValue, String salvageBuyer,
                       LocalDate salvageSoldAt, BigDecimal insuranceSettlementAmount,
                       LocalDate insuranceSettledAt) {
        if (assessmentDate != null && decisionDate.isBefore(assessmentDate)) {
            throw new IllegalArgumentException("The write-off decision cannot be dated before the assessment");
        }
        if (insuranceSettlementAmount != null && insuranceSettledAt == null) {
            insuranceSettledAt = LocalDate.now();
        }
        this.assessmentDate = assessmentDate;
        this.assessorName = assessorName;
        this.decisionDate = decisionDate;
        this.decisionNote = decisionNote;
        this.preAccidentValue = preAccidentValue;
        this.salvageValue = salvageValue;
        this.salvageBuyer = salvageBuyer;
        this.salvageSoldAt = salvageSoldAt;
        this.insuranceSettlementAmount = insuranceSettlementAmount;
        this.insuranceSettledAt = insuranceSettledAt;
    }
}