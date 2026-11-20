package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Search for conflicts before opening a matter for {@code clientId}. The client's own name and ID number are always
 * searched; {@code parties} adds everyone else involved (the other side, co-parties, related parties, witnesses).
 */
public record RunConflictCheckRequest(
        @NotNull UUID clientId,
        @Valid @Size(max = 25) List<Party> parties
) {
    /** {@code role}: OPPOSING_PARTY | CO_PARTY | RELATED_PARTY | WITNESS | OTHER (default OPPOSING_PARTY). */
    public record Party(@Size(max = 255) String name, @Size(max = 100) String idNumber, @Size(max = 20) String role) {}
}
