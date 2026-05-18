package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String email,
        String phone,
        Map<String, String> address,
        String taxNumber,
        String notes,
        Instant createdAt
) {}
