// security/application/internal/BranchService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Branch;
import za.co.handyflow.platform.security.domain.repository.BranchRepository;
import za.co.handyflow.platform.security.dto.CreateBranchRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * BranchService — creates and manages branches (geographic/organisational
 * sub-divisions of a tenant).
 *
 * Assigning sites and guards to branches is done via direct UPDATE on those
 * tables (site.branch_id, guard.primary_branch_id) rather than a join table,
 * because site-to-branch and guard-to-branch are both 1:1 primary assignments.
 * The security_branch_assignments many-to-many table covers the floating/matrix
 * case (a guard available to multiple branches for scheduling purposes), which
 * is managed separately.
 *
 * Scoping implications:
 * Once a user has a branch assignment with role=MANAGER, the API layer should
 * filter all tenant-scoped queries to that branch. This service provides the
 * branch management surface; the filtering itself is enforced at the controller
 * level by injecting the acting user's branch scope alongside the TenantId.
 * This is a future enforcement step — the current controllers still return
 * all-tenant data, with branch_id as a filter parameter the caller opts into.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    @Transactional
    public Branch createBranch(TenantId tenantId, CreateBranchRequest req) {
        if (branchRepository.existsByName(tenantId, req.name())) {
            throw new HandyFlowException(
                    "A branch named '" + req.name() + "' already exists",
                    HttpStatus.CONFLICT, "DUPLICATE_BRANCH_NAME");
        }
        Branch branch = Branch.create(tenantId, req.name(), req.region(), req.description());
        branchRepository.save(branch);
        log.info("[Branch] Created id={} name={}", branch.getId(), req.name());
        return branch;
    }

    @Transactional(readOnly = true)
    public List<Branch> listBranches(TenantId tenantId) {
        return branchRepository.findActiveBranches(tenantId);
    }

    @Transactional(readOnly = true)
    public Branch getBranch(TenantId tenantId, UUID id) {
        return branchRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id.toString()));
    }

    @Transactional
    public Branch updateBranch(TenantId tenantId, UUID id, CreateBranchRequest req) {
        Branch branch = getBranch(tenantId, id);
        if (!branch.getName().equals(req.name()) && branchRepository.existsByName(tenantId, req.name())) {
            throw new HandyFlowException(
                    "A branch named '" + req.name() + "' already exists",
                    HttpStatus.CONFLICT, "DUPLICATE_BRANCH_NAME");
        }
        branch.update(req.name(), req.region(), req.description());
        return branchRepository.save(branch);
    }

    @Transactional
    public void deactivateBranch(TenantId tenantId, UUID id) {
        Branch branch = getBranch(tenantId, id);
        branch.deactivate();
        branchRepository.save(branch);
        log.info("[Branch] Deactivated id={}", id);
    }
}
