package za.co.handyflow.platform.hr.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The result of the permissions migration for this tenant: what each role held, what it was given, and what needs a decision. */
public record PayrollAccessReviewResponse(LocalDate legacyAccessEnds, int rolesNeedingReview, List<Role> roles) {
    public record Role(UUID roleId, String roleName, int usersInRole, String held, String granted, boolean needsReview, String note) {}
}
