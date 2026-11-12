// security/dto/GuardMyWorkDtos.java
package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What the guard app shows a guard about their own work. Names only; no other guard's data. */
public final class GuardMyWorkDtos {
    private GuardMyWorkDtos() {}

    public record ShiftView(UUID id, UUID siteId, String siteName, Instant startAt, Instant endAt,
                            String status, Instant actualStartAt) {}

    public record RoundCheckpoint(UUID checkpointId, String name, int sequence, boolean scanned) {}

    public record RoundView(UUID id, int roundNumber, String status, Instant expectedStartAt, Instant expectedEndAt,
                            Instant startedAt, Instant completedAt, int checkpointsExpected, int checkpointsScanned,
                            List<RoundCheckpoint> checkpoints) {}
}
