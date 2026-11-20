package za.co.handyflow.platform.legalpractice.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DeadlinePreviewResponse(UUID ruleId, String ruleName, String triggerLabel, LocalDate triggerDate,
                                      LocalDate dueDate, String dueDayOfWeek, List<String> explanation) {}
