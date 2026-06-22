package za.co.handyflow.platform.clinic.dto;

// ── REPLACE your existing PatientResponse record with this one ────────────────
// Adds the P5 family account fields: accountType, principalId, principalName,
// relationship, lastVisitAt, archivedAt.

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full patient DTO returned by all /patients endpoints.
 *
 * WHY principalName as a denormalised string?
 * The patient list needs to show "child of Jane Smith" without a second API call.
 * We batch-load principal names in ClinicService.getPatients() and include them here.
 * The name is nullable — it's null for INDIVIDUAL and PRINCIPAL accounts.
 */
public record PatientResponse(
        UUID   id,
        String firstName,
        String lastName,
        String fullName,
        String idNumber,
        LocalDate dateOfBirth,
        String gender,
        String phone,
        String email,
        String bloodType,
        List<String> allergies,
        List<String> chronicConditions,
        String emergencyContactName,
        String emergencyContactPhone,
        String notes,
        boolean active,
        Instant createdAt,

        // ── P5: Family account fields ─────────────────────────────────────────
        String  accountType,     // INDIVIDUAL | PRINCIPAL | DEPENDANT
        UUID    principalId,     // null unless DEPENDANT
        String  principalName,   // denormalised from principal row — null unless DEPENDANT
        String  relationship,    // CHILD | PARENT | GRANDPARENT | SPOUSE | SIBLING | OTHER
        Instant lastVisitAt,     // denormalised from last consultation — null if no visits
        Instant archivedAt       // null = active; non-null = archived
) {}
