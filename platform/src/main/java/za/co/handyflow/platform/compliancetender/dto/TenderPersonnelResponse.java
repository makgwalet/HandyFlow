package za.co.handyflow.platform.compliancetender.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * employeeFullName/employeeNumber are looked up LIVE from HrFacade at
 * read time — never stored (see TenderPersonnel's own Javadoc). When
 * false, employeeFound means the HR record this tender referenced no
 * longer exists (e.g. the employee was since removed from HR) — the
 * honest tradeoff of a reference instead of a copy: nothing to silently
 * go stale, but a caller does need to handle "the thing being referenced
 * is gone" rather than always getting a name back.
 */
public record TenderPersonnelResponse(
        UUID id, UUID tenderId, UUID employeeId, String role,
        boolean employeeFound, String employeeFullName, String employeeNumber, Instant createdAt
) {}
