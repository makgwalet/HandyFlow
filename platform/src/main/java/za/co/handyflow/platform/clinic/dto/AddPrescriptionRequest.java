package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotBlank;

public record AddPrescriptionRequest(
        @NotBlank String medicationName,
        String dosage,
        String frequency,
        String duration,
        Integer quantity,
        Integer repeats,
        String instructions,
        String nappiCode,
        Integer schedule
) {
    /** Pre-NAPPI constructor kept so existing callers compile. */
    public AddPrescriptionRequest(String medicationName, String dosage, String frequency,
                                  String duration, Integer quantity, Integer repeats,
                                  String instructions) {
        this(medicationName, dosage, frequency, duration, quantity, repeats, instructions, null, null);
    }
}