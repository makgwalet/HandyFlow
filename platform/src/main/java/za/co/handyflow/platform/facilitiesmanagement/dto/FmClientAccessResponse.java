package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.util.List;
import java.util.UUID;

/** {@code restricted=false} means the user has no client restriction (full access to all clients). */
public record FmClientAccessResponse(UUID userId, boolean restricted, List<UUID> clientIds) {}