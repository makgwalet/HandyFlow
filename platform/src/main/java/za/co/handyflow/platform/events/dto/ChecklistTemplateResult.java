package za.co.handyflow.platform.events.dto;

/** How many starter items were added, and how many were left alone because the event already had one with the same phase and title. */
public record ChecklistTemplateResult(int added, int skipped) {}
