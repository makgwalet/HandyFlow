package za.co.handyflow.platform.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenantModuleResponse(
        UUID id,
        String moduleKey,
        String moduleName,
        String description,
        BigDecimal monthlyPrice,
        String status,
        Instant trialEndsAt,
        Instant activatedAt,
        boolean accessible
) {}