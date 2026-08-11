// security/dto/SetSiteBranchRequest.java
package za.co.handyflow.platform.security.dto;

import java.util.UUID;

/** branchId may be null to clear the site's branch assignment. */
public record SetSiteBranchRequest(
        UUID branchId
) {}