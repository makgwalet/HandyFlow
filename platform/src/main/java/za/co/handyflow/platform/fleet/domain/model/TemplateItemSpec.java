package za.co.handyflow.platform.fleet.domain.model;

/** Plain description of one checklist question, used to build / replace template items. */
public record TemplateItemSpec(
        String section,
        String label,
        boolean required,
        boolean photoRequiredOnFail,
        IssueSeverity severity
) {}