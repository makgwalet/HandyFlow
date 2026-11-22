package za.co.handyflow.platform.security.dto;

import java.time.Instant;

/** Whether a face template is held for the guard and when consent was recorded (null = before consent was recorded). */
public record FaceStatusResponse(boolean stored, Instant consentAt) {}
