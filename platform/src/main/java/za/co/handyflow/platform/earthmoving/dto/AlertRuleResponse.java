package za.co.handyflow.platform.earthmoving.dto;

import java.util.List;
import java.util.UUID;

/** usesAdminFallback: no first-level recipients are set, so company administrators are told instead. */
public record AlertRuleResponse(String alertType, boolean enabled, Integer escalateAfterMinutes, List<UUID> initialUserIds,
                                List<UUID> escalationUserIds, boolean usesAdminFallback, boolean canBeDisabled) {}
