package za.co.handyflow.platform.recruiter.dto;

import java.util.UUID;

public final class RecruiterCvMigrationDtos {
    private RecruiterCvMigrationDtos() {}

    /** One round of moving old CVs into file storage. Call again with {@code nextAfter} until {@code done}. {@code unreadable} CVs are left as they were. */
    public record Result(int moved, int unreadable, boolean done, UUID nextAfter) {}
}
