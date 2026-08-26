package za.co.handyflow.platform.hr.dto;

import za.co.handyflow.platform.hr.domain.model.DisciplinaryOutcome;

import java.time.LocalDate;

/**
 * FIX: backlog 3.5 — the type-safety itself. outcome is a real enum
 * here, not a String, so Jackson rejects anything outside the four
 * defined values with a 400 before it ever reaches the service —
 * stronger than a domain-layer string guard, and the reason this is a
 * genuinely new request DTO rather than reusing AddDisciplinaryRequest.
 */
public record RecordDisciplinaryOutcomeRequest(
        DisciplinaryOutcome outcome,
        LocalDate hearingDate
) {}