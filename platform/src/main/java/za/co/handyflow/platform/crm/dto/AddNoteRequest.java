package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AddNoteRequest — adds a timestamped note to a customer's activity timeline.
 *
 * WHY a dedicated endpoint instead of updating the notes field on the Customer?
 * The single `notes` text field on Customer is a freeform scratch pad.
 * The activity timeline note is a timestamped, attributed record.
 * These are different use cases:
 * - notes field: "Key account, handles mining equipment contracts"
 * - activity note: "2024-06-12 · John: Called to follow up on quote #Q-0042"
 *
 * The timeline gives you WHO said WHAT and WHEN.  The notes field can't do that.
 */
public record AddNoteRequest(
        @NotBlank(message = "Note content is required")
        @Size(max = 5000, message = "Note cannot exceed 5000 characters")
        String note
) {}
