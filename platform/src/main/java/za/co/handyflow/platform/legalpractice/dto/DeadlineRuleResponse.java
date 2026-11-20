package za.co.handyflow.platform.legalpractice.dto;

import java.util.UUID;

public record DeadlineRuleResponse(UUID id, String code, String name, String description, String court, String triggerLabel,
                                   String keyDateType, int amount, String unit, String direction, String rollover,
                                   String recessStart, String recessEnd, String sourceReference, boolean active) {}
