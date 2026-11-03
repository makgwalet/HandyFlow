package za.co.handyflow.platform.projects.domain.enums;

public enum TaskStatus {
    NOT_STARTED,
    IN_PROGRESS,
    IN_REVIEW,      // work done, waiting for someone to check and sign it off
    COMPLETED,
    BLOCKED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == IN_PROGRESS || this == IN_REVIEW;
    }
}
