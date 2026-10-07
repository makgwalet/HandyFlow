package za.co.handyflow.platform.clinic.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class RoomDtos {
    private RoomDtos() {}

    public record CreateRoomRequest(@NotNull String name) {}

    /** Either field may be null to leave it as it is. */
    public record UpdateRoomRequest(String name, Boolean active) {}

    public record RoomResponse(UUID id, String name, boolean active) {}
}
