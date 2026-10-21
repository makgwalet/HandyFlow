package za.co.handyflow.platform.crm.dto;

import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.ConditionField;
import za.co.handyflow.platform.crm.domain.model.ConditionOp;

public record ScoringRuleResponse(UUID id, String name, ConditionField field, ConditionOp op, String value, int points, boolean active, String description) {}
