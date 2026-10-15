package za.co.handyflow.platform.earthmoving.dto;

import java.util.List;
import java.util.UUID;

public record SaveAlertRuleRequest(Boolean enabled, Integer escalateAfterMinutes, List<UUID> initialUserIds, List<UUID> escalationUserIds) {}
