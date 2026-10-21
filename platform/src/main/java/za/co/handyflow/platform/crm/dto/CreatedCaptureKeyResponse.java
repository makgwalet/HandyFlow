package za.co.handyflow.platform.crm.dto;

/** The plain key is shown once, here, and can never be retrieved again. */
public record CreatedCaptureKeyResponse(CaptureKeyResponse key, String apiKey) {}
