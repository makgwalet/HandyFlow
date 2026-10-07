// security/dto/PatrolDtos.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Patrol rounds across shifts: the list, and one round checkpoint by checkpoint. */
public final class PatrolDtos {
    private PatrolDtos() {}

    public record RoundRow(UUID id, UUID shiftId, UUID siteId, String siteName, String routeName, String guardName,
                           int roundNumber, String status, Instant expectedStartAt, Instant expectedEndAt,
                           Instant startedAt, Instant completedAt, int checkpointsExpected, int checkpointsScanned,
                           boolean offSchedule, String offScheduleReason, boolean acknowledged) {}

    public record CheckpointRow(UUID id, String name, int sequence, Instant scannedAt, String method, String scannedBy) {}

    public record RoundDetail(RoundRow round, String acknowledgementNote, List<CheckpointRow> checkpoints) {}

    public record AcknowledgeRequest(@NotBlank @Size(max = 1000) String note) {}
}
