package za.co.handyflow.platform.clinic.dto;

/** Body of POST /consultations/{id}/sign. {@code overrideReason} is only needed when a required step is missing. */
public record SignRequest(String overrideReason) {}
