package za.co.handyflow.platform.billing.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String name,
        String displayName,
        String description,
        int priceInRands,
        int maxUsers,
        int includedModuleCount,
        Set<String> includedModules,
        Map<String, Object> features
) {}