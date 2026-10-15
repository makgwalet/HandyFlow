package za.co.handyflow.platform.earthmoving.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The checklist in force for one inspection type. {@code source} is COMPANY for a published version, BUILT_IN when the company has none yet. */
public record ActiveChecklistResponse(UUID versionId, Integer versionNo, String inspectionType, boolean requiresSignoff, LocalDate effectiveFrom,
                                      String source, List<ChecklistQuestionDto> questions) {}
