package za.co.handyflow.platform.projects.dto;

import java.time.Instant;
import java.util.UUID;

/** One line of a project's recent activity. kind: STATUS | CHANGE_ORDER | RFI | DOCUMENT | MEMBER | BUDGET | ISSUE | DETAILS | CONTRACT */
public record ActivityItem(String kind, String summary, String detail, String actorName, Instant at, UUID refId) {}
