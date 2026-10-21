package za.co.handyflow.platform.crm.dto;

import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.WorkflowAction;

public record JourneyStepResponse(int position, int dayOffset, WorkflowAction action, String text, UUID user, String description) {}
