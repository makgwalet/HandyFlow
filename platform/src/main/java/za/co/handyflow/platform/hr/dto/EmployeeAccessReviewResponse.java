package za.co.handyflow.platform.hr.dto;

import java.util.List;
import java.util.UUID;

/** What the employee-security migration gave each role, and which roles lose something and need a decision. */
public record EmployeeAccessReviewResponse(int needsReview, List<Role> roles) {
    public record Role(UUID roleId, String roleName, int usersInRole, String legacyHeld, String granted, boolean needsReview, String note) {}
}
