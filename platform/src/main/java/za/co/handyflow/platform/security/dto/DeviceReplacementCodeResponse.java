package za.co.handyflow.platform.security.dto;

import java.time.Instant;

public record DeviceReplacementCodeResponse(String code, Instant expiresAt) {}
