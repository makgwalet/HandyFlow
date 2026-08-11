// security/dto/CreateGuardRequest.java
package za.co.handyflow.platform.security.dto;

import java.time.LocalDate;

/**
 * CreateGuardRequest — CHANGE: added emergencyContactName/emergencyContactPhone.
 *
 * NOTE: I do not have this file's previously-existing content -- reconstructed
 * from GuardService's usage (req.firstName(), req.lastName(), req.psiraNumber(),
 * req.idNumber(), req.phone(), req.grade(), req.notes(), req.psiraExpiryDate()).
 * The two new fields are appended at the end. Diff against your actual file
 * before applying -- same caveat as every other reconstructed DTO this
 * session (PrincipalResponse, GuardResponse, SiteResponse, etc).
 */
public record CreateGuardRequest(
        String    firstName,
        String    lastName,
        String    psiraNumber,
        String    idNumber,
        String    phone,
        String    grade,
        String    notes,
        LocalDate psiraExpiryDate,
        String    emergencyContactName,
        String    emergencyContactPhone
) {}