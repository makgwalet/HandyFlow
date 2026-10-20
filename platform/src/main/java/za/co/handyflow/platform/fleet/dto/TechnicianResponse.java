package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TechnicianResponse(
        UUID id,
        String name,
        String phone,
        String email,
        BigDecimal hourlyRate,
        boolean active
) {}