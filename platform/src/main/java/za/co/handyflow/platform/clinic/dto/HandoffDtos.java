package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

public final class HandoffDtos {
    private HandoffDtos() {}

    /** Optional note from the nurse when handing over. */
    public record SendToDoctorRequest(String comment) {}

    /** The doctor taking the consultation; practitionerId is optional (kept on the consultation as the reviewer). */
    public record AcceptHandoffRequest(UUID practitionerId) {}

    /** reasonCode is one of ReturnReason; comment is mandatory. */
    public record ReturnToNurseRequest(String reasonCode, String comment) {}

    public record TransitionResponse(UUID id, UUID consultationId, String fromStatus, String toStatus,
                                     UUID actorUserId, String reasonCode, String comment, Instant createdAt) {}
}
