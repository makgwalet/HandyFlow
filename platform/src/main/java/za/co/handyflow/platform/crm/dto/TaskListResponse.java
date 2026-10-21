package za.co.handyflow.platform.crm.dto;

import java.util.List;

/** truncated is true when more tasks exist than the queue returns in one go. */
public record TaskListResponse(List<TaskResponse> tasks, boolean truncated) {}
