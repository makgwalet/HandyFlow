// security/dto/GuardOpenSessionRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Clock-in from the guard app. Deliberately has no guardId, pinVerified, face score or
 * geofence result: the server knows who the guard is (token), that the PIN passed (token
 * issue) and whether they are on site (position + site). The phone only reports where it
 * is. Position is optional so a failed GPS fix does not stop a clock-in; it is then
 * recorded as "geofence unknown", not as passed.
 */
public record GuardOpenSessionRequest(
        @NotBlank String deviceHardwareId,
        @DecimalMin("-90")  @DecimalMax("90")  BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        Double accuracyMetres
) {}
