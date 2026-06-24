package za.co.handyflow.platform.projects.dto;

public record RiskActionRequest(
        String  action,   // MITIGATE|CLOSE|ACCEPT
        String  notes
) {}
