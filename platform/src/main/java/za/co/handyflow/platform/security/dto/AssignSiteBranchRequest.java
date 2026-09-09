// security/dto/AssignSiteBranchRequest.java
package za.co.handyflow.platform.security.dto;

import java.util.UUID;

// branchId is deliberately nullable -- Site.assignBranch(null) clears the
// assignment, matching Guard.setPrimaryBranch()'s same convention.
public record AssignSiteBranchRequest(
        UUID branchId
) {}
