package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.crm.domain.model.ConditionField;
import za.co.handyflow.platform.crm.domain.model.ConditionOp;

public record ScoringRuleRequest(String name, @NotNull ConditionField field, @NotNull ConditionOp op, String value, @NotNull Integer points, Boolean active) {}
