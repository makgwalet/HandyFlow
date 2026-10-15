package za.co.handyflow.platform.projects;

import java.util.UUID;

/** What other modules may know about a project. clientId is the CRM customer the project is for, when set. */
public record ProjectSummary(UUID id, String projectNumber, String name, UUID clientId, String clientName, String siteAddress, String status) {}
