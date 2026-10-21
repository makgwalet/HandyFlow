package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.HrOvertimeClaim;
import za.co.handyflow.platform.hr.domain.model.OvertimeRules;
import za.co.handyflow.platform.hr.domain.repository.HrOvertimeClaimRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the attendance record needs to know about overtime claims, without depending on the service that makes them: the claim
 * made for a day, and whether agreed pay means the day's time must not change.
 */
@Component
@RequiredArgsConstructor
class OvertimeClaimLookup {

    private final HrOvertimeClaimRepository claimRepo;

    /** A reference to the claim made for a day. */
    record ClaimRef(UUID id, String status) {}

    Map<LocalDate, ClaimRef> claimsFor(TenantId tenantId, UUID employeeId, LocalDate from, LocalDate to) {
        Map<LocalDate, ClaimRef> out = new HashMap<>();
        for (HrOvertimeClaim c : claimRepo.forEmployee(tenantId, employeeId, from, to)) out.put(c.getWorkDate(), new ClaimRef(c.getId(), c.getStatus()));
        return out;
    }

    /** Why the entries of this day cannot change, or null. */
    String lockReason(TenantId tenantId, UUID employeeId, LocalDate date) {
        return claimRepo.forDay(tenantId, employeeId, date)
                .map(c -> OvertimeRules.lockReason(c.getStatus(), c.getPayRunId() != null)).orElse(null);
    }
}
