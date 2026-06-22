package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * NRPL (National Reference Price List) tariff codes.
 * Global table — no tenant_id. Seeded via V79 migration.
 * Practices cannot add custom entries (NRPL codes are gazetted annually).
 *
 * WHY separate from ClinicMedicationCatalogue?
 * Procedures (tariff codes) and medicines (NAPPI codes) are governed by
 * different regulatory frameworks and pricing authorities — NRPL vs. SEP.
 */
@Entity
@Table(name = "clinic_procedure_catalogue")
@Getter
@NoArgsConstructor
public class ClinicProcedureCatalogue {

    @Id UUID id;
    @Column(name = "tariff_code")  String tariffCode;
    String description;
    String specialty;
    @Column(name = "base_rate_zar") BigDecimal baseRateZar;
    boolean active = true;
}
