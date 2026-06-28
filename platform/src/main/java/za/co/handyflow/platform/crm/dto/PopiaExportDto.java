package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PopiaExportDto — the complete POPIA data subject export payload.
 *
 * This is the canonical structure for the JSON export file.
 * Every field maps directly to what POPIA Section 23 requires:
 * "all personal information held about the data subject."
 *
 * WHY nested records?
 * The export has clear sections: metadata (when, by whom), personal data
 * (the customer fields), and processing history (the activity log).
 * Flat DTOs lose this structure and make the JSON harder to read for
 * compliance officers or the Information Regulator.
 *
 * FIELD NAMING:
 * Fields use camelCase (standard JSON).  The export file itself has a
 * preamble comment (in the JSON via a "_comment" key) explaining each
 * section for non-technical readers.
 */
public record PopiaExportDto(

        UUID        customerId,

        /**
         * Timestamp of when this export was generated.
         * Required for audit purposes — the Information Regulator can ask
         * "when did you send this data to the subject?"
         */
        Instant     exportedAt,

        /**
         * ID of the staff member who generated the export.
         * Required — POPIA mandates accountability for who accesses data.
         */
        UUID        exportedBy,

        PersonalData personalData,

        List<ActivityEntry> processingHistory

) {

    /**
     * PersonalData — every personal information field held on the customer.
     * deletedAt is included (if present) so the subject knows if/when
     * their record was soft-deleted.
     */
    public record PersonalData(
            UUID                id,
            String              name,
            String              email,
            String              phone,
            Map<String, String> address,
            String              taxNumber,
            String              notes,
            String              customerType,
            String              status,
            Set<String>         tags,
            Instant             createdAt,
            Instant             updatedAt,
            Instant             deletedAt    // null if not deleted
    ) {}

    /**
     * ActivityEntry — a single processing history event.
     * Shows WHAT was changed, WHEN, and BY WHOM.
     * This directly answers POPIA Section 23(1)(b):
     * "the identity of third parties who had access to the information."
     * In our case, "third parties" means other staff (performedBy).
     */
    public record ActivityEntry(
            UUID                    id,
            String                  activityType,
            Map<String, Object>     payload,      // what changed (field diffs)
            String                  note,
            UUID                    performedBy,  // null = system-automated
            Instant                 createdAt
    ) {}
}
