package za.co.handyflow.platform.crm;

import java.util.UUID;

public record CustomerSummary(
        UUID id,
        String name,
        String email,
        String phone,
        String taxNumber
) {}
