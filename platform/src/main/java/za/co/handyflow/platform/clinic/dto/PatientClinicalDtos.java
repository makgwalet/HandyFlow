package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Request/response shapes for a patient's structured allergies, conditions and medications. */
public final class PatientClinicalDtos {
    private PatientClinicalDtos() {}

    public record AllergyRequest(String allergen, String allergenType, String reaction,
                                 String severity, String status, String notes) {}

    public record AllergyResponse(UUID id, UUID patientId, String allergen, String allergenType,
                                  String reaction, String severity, String status, String notes,
                                  Instant createdAt, Instant updatedAt) {}

    public record ConditionRequest(String conditionName, String icd10Code, LocalDate onsetDate,
                                   String status, String notes) {}

    public record ConditionResponse(UUID id, UUID patientId, String conditionName, String icd10Code,
                                    String status, LocalDate onsetDate, String notes,
                                    Instant createdAt, Instant updatedAt) {}

    public record MedicationRequest(String medicineName, String nappiCode, String dose, String frequency,
                                    String status, String source, LocalDate startedOn,
                                    LocalDate stoppedOn, String stopReason, String notes) {}

    public record MedicationResponse(UUID id, UUID patientId, String medicineName, String nappiCode,
                                     String dose, String frequency, String status, String source,
                                     LocalDate startedOn, LocalDate stoppedOn, String stopReason,
                                     UUID prescriptionId, String notes,
                                     Instant createdAt, Instant updatedAt) {}
}
