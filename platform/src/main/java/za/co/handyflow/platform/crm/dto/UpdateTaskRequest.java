package za.co.handyflow.platform.crm.dto;

import za.co.handyflow.platform.crm.domain.model.CustomerFollowUp;

import java.time.LocalDate;
import java.util.UUID;

/** Any field left null is left as it is. */
public record UpdateTaskRequest(UUID assignedTo, LocalDate dueDate, CustomerFollowUp.Priority priority) {}
