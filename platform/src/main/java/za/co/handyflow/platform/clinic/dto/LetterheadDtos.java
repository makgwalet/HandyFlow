package za.co.handyflow.platform.clinic.dto;

import java.util.List;
import java.util.UUID;

/** Practice and branch letterheads (patch 0193). */
public final class LetterheadDtos {
    private LetterheadDtos() {}

    public record LetterheadResponse(UUID id, String name, String branchName, String practiceName, List<String> lines,
                                     boolean isDefault, boolean active, boolean hasLogo) {}

    public record LetterheadRequest(String name, String branchName, String practiceName, List<String> lines, Boolean makeDefault) {}
}
