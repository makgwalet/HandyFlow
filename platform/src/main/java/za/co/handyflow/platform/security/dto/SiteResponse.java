// security/dto/SiteResponse.java
package za.co.handyflow.platform.security.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SiteResponse — CHANGE: added requireSignedQr and branchId, appended at
 * the end. Real gap found, not a preemptive addition: both fields were
 * added to the Site entity earlier this session (V215, V218) but this
 * response record was never updated to expose either one -- meaning the
 * frontend had no way to know a site's current QR-enforcement or
 * branch-assignment state even before any UI was built for them.
 *
 * NOTE: I do not have this file's previously-existing content -- reconstructed
 * from SiteService.toResponse()/toResponseWithCheckpoints()'s 16-argument
 * constructor calls, which have compiled successfully in this codebase
 * before this change. Diff against your actual file before applying, same
 * caveat as PrincipalResponse/GuardResponse earlier this session.
 */
public record SiteResponse(
        UUID       id,
        String     name,
        UUID       customerId,
        Map<String, String> address,
        BigDecimal latitude,
        BigDecimal longitude,
        String     contactName,
        String     contactPhone,
        boolean    active,
        List<CheckpointResponse> checkpoints,
        String     contractStatus,
        LocalDate  contractStart,
        LocalDate  contractEnd,
        String     terminationReason,
        Instant    terminatedAt,
        Instant    createdAt,
        boolean    requireSignedQr,
        UUID       branchId
) {}