package za.co.handyflow.platform.crm.dto;

import java.util.List;
import java.util.UUID;

/** filled lists the fields the kept record gained. consentNote is set when the removed record's consent was not moved. */
public record MergeResultResponse(UUID id, String name, List<String> filled, MergePreviewResponse.Moves moved, String consentNote) {}
