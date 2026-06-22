package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinic_medication_catalogue")
@Getter
@NoArgsConstructor
public class ClinicMedicationCatalogue {

    @Id UUID id;
    @Column(name = "tenant_id")   UUID   tenantId;   // null = system/global entry
    @Column(name = "nappi_code")  String nappiCode;
    @Column(name = "generic_name") String genericName;
    @Column(name = "brand_name")  String brandName;
    @Column(name = "dosage_form") String dosageForm;
    String strength;
    Integer schedule;
    @Column(name = "single_exit_price") BigDecimal singleExitPrice;
    boolean active = true;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
}
