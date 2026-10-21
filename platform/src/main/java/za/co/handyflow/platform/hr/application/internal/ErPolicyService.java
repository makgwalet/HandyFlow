package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.ErPolicy;
import za.co.handyflow.platform.hr.domain.model.ErRules;
import za.co.handyflow.platform.hr.domain.model.HrErPolicy;
import za.co.handyflow.platform.hr.domain.repository.HrErPolicyRepository;
import za.co.handyflow.platform.hr.dto.ErDtos.PolicyRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Set;
import java.util.UUID;

/** The company's own employee relations settings (notice hours, how long warnings last, appeal and grievance times). */
@Service
@RequiredArgsConstructor
public class ErPolicyService {

    private final HrErPolicyRepository repo;

    @Transactional(readOnly = true)
    public ErPolicy get(TenantId tenantId) {
        return repo.findById(tenantId.getValue()).map(HrErPolicy::toPolicy).orElse(ErPolicy.defaults());
    }

    @Transactional
    public ErPolicy save(TenantId tenantId, PolicyRequest req, UUID by, Set<String> held) {
        if (!ErRules.canDecide(held)) throw new AccessDeniedException("Only people who may decide employee relations cases can change these settings.");
        ErPolicy p = new ErPolicy(req.noticeHours(), req.verbalMonths(), req.writtenMonths(), req.finalMonths(), req.appealDays(),
                req.grievanceResponseDays(), req.minReviewDays());
        String problem = p.problem();
        if (problem != null) throw new IllegalArgumentException(problem);
        HrErPolicy row = repo.findById(tenantId.getValue()).orElse(null);
        if (row == null) row = HrErPolicy.of(tenantId.getValue(), p, by);
        else row.apply(p, by);
        repo.save(row);
        return p;
    }
}
