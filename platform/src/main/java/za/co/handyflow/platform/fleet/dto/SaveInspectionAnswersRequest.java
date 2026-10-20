package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Bulk save — send one answer or the whole checklist; handy for saving progress from a phone. */
public record SaveInspectionAnswersRequest(
        @NotEmpty @Size(max = 500) List<@Valid InspectionAnswerRequest> answers
) {}