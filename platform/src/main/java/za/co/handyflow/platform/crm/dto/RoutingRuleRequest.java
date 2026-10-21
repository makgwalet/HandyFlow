package za.co.handyflow.platform.crm.dto;

import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.ConditionField;
import za.co.handyflow.platform.crm.domain.model.ConditionOp;

/** field, op and value are all empty for a rule that matches every lead. */
public record RoutingRuleRequest(String name, ConditionField field, ConditionOp op, String value, List<UUID> assigneeIds, Boolean active) {}
