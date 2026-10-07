package za.co.handyflow.platform.clinic.domain.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Identifiers and canonical units for observations. This is a naming catalogue only:
 * it carries no clinical thresholds. Reference ranges, when wanted, are supplied with each
 * observation (or later come from reviewed reference content, see DEC-CLINIC-001).
 */
public enum ObservationCode {
    BP_SYSTOLIC("Systolic blood pressure", "mmHg"),
    BP_DIASTOLIC("Diastolic blood pressure", "mmHg"),
    PULSE("Pulse", "bpm"),
    RESP_RATE("Respiratory rate", "/min"),
    TEMPERATURE("Temperature", "C"),
    SPO2("Oxygen saturation", "%"),
    WEIGHT("Weight", "kg"),
    HEIGHT("Height", "cm"),
    BMI("Body mass index", "kg/m2"),
    GLUCOSE("Blood glucose", "mmol/L"),
    HBA1C("HbA1c", "%"),
    MUAC("Mid-upper arm circumference", "cm"),
    HEAD_CIRCUMFERENCE("Head circumference", "cm"),
    PEFR("Peak expiratory flow", "L/min"),
    PAIN_SCORE("Pain score", "score");

    private final String label;
    private final String unit;

    ObservationCode(String label, String unit) { this.label = label; this.unit = unit; }

    public String label() { return label; }
    public String unit()  { return unit; }

    public static Optional<ObservationCode> parse(String raw) {
        if (raw == null) return Optional.empty();
        String u = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return Arrays.stream(values()).filter(c -> c.name().equals(u)).findFirst();
    }
}
