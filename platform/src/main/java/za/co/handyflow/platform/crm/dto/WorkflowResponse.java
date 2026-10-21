package za.co.handyflow.platform.crm.dto;

import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.ConditionField;
import za.co.handyflow.platform.crm.domain.model.ConditionOp;
import za.co.handyflow.platform.crm.domain.model.LeadStage;
import za.co.handyflow.platform.crm.domain.model.WorkflowAction;
import za.co.handyflow.platform.crm.domain.model.WorkflowTrigger;

public record WorkflowResponse(UUID id, String name, WorkflowTrigger trigger, LeadStage triggerStage, ConditionField condField, ConditionOp condOp, String condValue,
                               WorkflowAction action, String actionText, Integer actionDays, UUID actionUser, boolean active, String description) {}
