package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ClientContactResponse(
        UUID id,
        String role,
        String fullName,
        String idNumber,
        String email,
        String phone,
        BigDecimal percentageHeld
) {
}
