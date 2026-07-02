package za.co.handyflow.platform.projects.domain.enums;

public enum TaskStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    BLOCKED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == IN_PROGRESS;
    }
}
