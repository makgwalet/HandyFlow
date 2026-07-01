// security/dto/SetCpVettingTierRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SetCpVettingTierRequest(
        @NotBlank String tier,             // STANDARD | ENHANCED | HIGH | CRITICAL
        @NotNull  LocalDate clearedAt,
        LocalDate expiresAt
) {}
