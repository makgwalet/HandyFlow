package za.co.handyflow.platform.crm.dto;

import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.ConditionField;
import za.co.handyflow.platform.crm.domain.model.ConditionOp;
import za.co.handyflow.platform.crm.domain.model.LeadStage;
import za.co.handyflow.platform.crm.domain.model.WorkflowAction;
import za.co.handyflow.platform.crm.domain.model.WorkflowTrigger;

/** condField/condOp/condValue are all empty for "no extra condition". triggerStage only applies to STAGE_CHANGED. */
public record WorkflowRequest(String name, WorkflowTrigger trigger, LeadStage triggerStage, ConditionField condField, ConditionOp condOp, String condValue,
                              WorkflowAction action, String actionText, Integer actionDays, UUID actionUser, Boolean active) {}
