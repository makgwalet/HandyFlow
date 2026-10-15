package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Replaces the questions of a draft. {@code requiresSignoff} makes the inspector type a name when the inspection is saved. */
public record SaveChecklistDraftRequest(boolean requiresSignoff, String note, @NotEmpty List<ChecklistQuestionDto> questions) {}
