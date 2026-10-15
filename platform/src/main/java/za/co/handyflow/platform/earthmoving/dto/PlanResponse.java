package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PlanResponse(UUID id, UUID assetId, String name, String taskType, String triggerType, int intervalValue,
                           int leadValue, BigDecimal lastDoneHours, LocalDate lastDoneDate, String priority,
                           boolean blocksRelease, boolean active, String state, BigDecimal used, BigDecimal remaining,
                           String unit) {}
