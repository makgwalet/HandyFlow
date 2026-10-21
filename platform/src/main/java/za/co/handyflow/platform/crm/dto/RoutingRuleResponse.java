package za.co.handyflow.platform.crm.dto;

import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.ConditionField;
import za.co.handyflow.platform.crm.domain.model.ConditionOp;

public record RoutingRuleResponse(UUID id, String name, int position, ConditionField field, ConditionOp op, String value, String description, List<UUID> assigneeIds, boolean active) {}
