package za.co.handyflow.platform.projects.domain.enums;

/**
 * RAG (Red / Amber / Green) health rating for a project.
 *
 * Keeping this as a separate enum from ProjectStatus lets us express
 * "the project is ACTIVE but its health is RED" cleanly — two orthogonal
 * dimensions that were previously just two String fields.
 */
public enum ProjectHealth {
    GREEN,   // within budget and schedule tolerances
    AMBER,   // approaching thresholds — needs attention
    RED;     // breached thresholds — escalation required

    /**
     * Whether the project needs immediate management attention.
     * Used by dashboard filters and notification triggers.
     */
    public boolean requiresAttention() {
        return this == AMBER || this == RED;
    }
}
