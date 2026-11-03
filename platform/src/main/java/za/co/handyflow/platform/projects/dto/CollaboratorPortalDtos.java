package za.co.handyflow.platform.projects.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Shapes of the public collaborator workspace. OTP and session shapes are shared with the single-link page. */
public final class CollaboratorPortalDtos {

    private CollaboratorPortalDtos() {}

    /** One transmittal issued to this collaborator. recipientId is what the download and acknowledge calls take. */
    public record Entry(UUID recipientId, RecipientPortalDtos.View transmittal) {}

    public record Workspace(String name, String organisation, Instant expiresAt, List<Entry> transmittals) {}
}
