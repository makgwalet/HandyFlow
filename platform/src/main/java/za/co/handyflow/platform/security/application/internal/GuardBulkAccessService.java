package za.co.handyflow.platform.security.application.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.dto.BulkAppAccessRequest;
import za.co.handyflow.platform.security.dto.BulkAppAccessResponse;
import za.co.handyflow.platform.security.dto.BulkAppAccessResponse.Item;
import za.co.handyflow.platform.security.dto.GuardEnrollRequest;
import za.co.handyflow.platform.shared.TenantId;

/**
 * Gives many guards mobile-app access at once: each gets a generated temporary PIN that must be changed at first login.
 * Deliberately NOT one big transaction: each guard is enrolled on its own, so one failure never rolls back the rest.
 * A guard record exists for payroll and rostering whether or not the guard has the app; this only adds app access.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardBulkAccessService {

    private final GuardRepository guardRepository;
    private final GuardAuthService guardAuthService;

    public BulkAppAccessResponse provision(TenantId tenantId, BulkAppAccessRequest req, UUID supervisorId) {
        boolean reset = req != null && req.resetExisting();
        List<UUID> ids = BulkAccessRules.normalizeIds(req == null ? null : req.guardIds());

        List<Guard> guards = new ArrayList<>();
        List<Item> results = new ArrayList<>();
        boolean more = false;

        if (ids.isEmpty()) {
            // Everyone eligible: active guards without app access yet (or all active ones when resetting).
            for (Guard g : guardRepository.findAllActiveList(tenantId)) {
                if (BulkAccessRules.classify(g.getStatus(), g.getPinHash() != null, reset)
                        == BulkAccessRules.Eligibility.PROVISION) guards.add(g);
            }
            if (guards.size() > BulkAccessRules.MAX_PER_REQUEST) {
                guards = new ArrayList<>(guards.subList(0, BulkAccessRules.MAX_PER_REQUEST));
                more = true;
            }
        } else {
            for (UUID id : ids) {
                var g = guardRepository.findByIdAndTenantForAuth(id, tenantId);
                if (g.isPresent()) guards.add(g.get());
                else results.add(new Item(id, null, null, null, "FAILED", null, "Guard not found"));
            }
        }

        int provisioned = 0, skipped = 0, failed = results.size();
        for (Guard g : guards) {
            var elig = BulkAccessRules.classify(g.getStatus(), g.getPinHash() != null, reset);
            if (elig != BulkAccessRules.Eligibility.PROVISION) {
                skipped++;
                results.add(item(g, elig == BulkAccessRules.Eligibility.SKIP_NOT_ACTIVE
                        ? "SKIPPED_NOT_ACTIVE" : "SKIPPED_ALREADY_ENROLLED", null, null));
                continue;
            }
            try {
                var resp = guardAuthService.enroll(tenantId, g.getId(),
                        new GuardEnrollRequest(null, null, null, null), supervisorId);
                provisioned++;
                results.add(item(g, "PROVISIONED", resp.temporaryPin(), null));
            } catch (Exception e) {
                failed++;
                log.warn("[Security] Bulk app access failed guard={} tenant={}: {}", g.getId(), tenantId, e.getMessage());
                results.add(item(g, "FAILED", null, "Could not enrol this guard"));
            }
        }
        log.info("[Security] Bulk app access tenant={} by={} provisioned={} skipped={} failed={}",
                tenantId, supervisorId, provisioned, skipped, failed);
        return new BulkAppAccessResponse(provisioned, skipped, failed, more, results);
    }

    /** A sheet to hand out or send by SMS. Contains temporary PINs: treat like a password list. */
    public String toCsv(BulkAppAccessResponse r) {
        StringBuilder b = new StringBuilder(BulkAccessRules.csvLine(
                "Name", "Employee code", "Phone", "Result", "Temporary PIN", "Note"));
        for (Item i : r.results()) {
            b.append(BulkAccessRules.csvLine(i.fullName(), i.employeeCode(), i.phone(), i.outcome(),
                    i.temporaryPin(), i.message()));
        }
        return b.toString();
    }

    private static Item item(Guard g, String outcome, String pin, String msg) {
        return new Item(g.getId(), g.getFullName(), g.getEmployeeCode(), g.getPhone(), outcome, pin, msg);
    }
}
