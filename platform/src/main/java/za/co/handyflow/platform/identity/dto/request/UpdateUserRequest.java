package za.co.handyflow.platform.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// FIX (identity module modernization): this record previously had zero
// validation annotations, and UserManagementService.updateUser() only
// null-checks each field before applying it ("" is not null). Combined
// with TeamTab.tsx's Edit User modal not enforcing required fields
// either, an admin could submit a blank first/last name and silently
// wipe it — confirmed reachable end to end, not a theoretical gap.
// firstName/lastName are made @NotBlank (required on every call) rather
// than kept "null = skip" like the other fields below: the one real
// caller (TeamTab.tsx's edit-user form) always submits both together
// already, pre-filled from the current values, so this doesn't remove
// any capability that was actually reachable — it just turns "silently
// save a blank name" into a 400 the caller has to handle. phone/
// jobTitle/department stay genuinely optional and unvalidated beyond a
// length cap — null continues to mean "don't touch this field", exactly
// as the service's own null-checks already assume, and the one real
// caller already normalizes "" to null before sending (TeamTab.tsx:
// `jobTitle: jt || null, department: dept || null`), so no existing
// behaviour changes for those three fields beyond the new length cap.
public record UpdateUserRequest(
        @NotBlank(message = "First name cannot be blank")
        @Size(max = 100, message = "First name must be 100 characters or fewer")
        String firstName,
        @NotBlank(message = "Last name cannot be blank")
        @Size(max = 100, message = "Last name must be 100 characters or fewer")
        String lastName,
        @Size(max = 30, message = "Phone number must be 30 characters or fewer")
        String phone,
        @Size(max = 150, message = "Job title must be 150 characters or fewer")
        String jobTitle,
        @Size(max = 150, message = "Department must be 150 characters or fewer")
        String department,
        java.util.UUID roleId   // null = don't change role
) {}
