package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.SnagItem;

import java.time.LocalDate;

/** Client-safe snag view — no assignee/subcontractor identity, no photo URLs, no internal description. */
public record PortalSnagResponse(
        String    snagNumber,
        String    title,
        String    location,
        String    severity,
        String    status,
        LocalDate dueDate
) {
    public static PortalSnagResponse of(SnagItem s) {
        return new PortalSnagResponse(
                s.getSnagNumber(), s.getTitle(), s.getLocation(),
                s.getSeverity(), s.getStatus(), s.getDueDate());
    }
}