package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.EmployeeDraftRules;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeDraft;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeDraftRepository;
import za.co.handyflow.platform.hr.dto.EmployeeDraftDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Each person's own half-finished employee registrations. Nobody else's drafts are ever visible. */
@Service
@RequiredArgsConstructor
public class EmployeeDraftService {

    private final HrEmployeeDraftRepository drafts;

    @Transactional
    public List<Summary> list(TenantId tenantId, UUID owner) {
        drafts.deleteOlderThan(tenantId, Instant.now().minus(EmployeeDraftRules.KEEP_FOR));
        return drafts.mine(tenantId, owner).stream().map(d -> new Summary(d.getId(), d.getLabel(), d.getStep(), d.getUpdatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public Detail get(TenantId tenantId, UUID owner, UUID id) {
        return detail(find(tenantId, owner, id));
    }

    /** Creates the draft, or replaces the one with this id if it is the caller's. */
    @Transactional
    public Detail save(TenantId tenantId, UUID owner, UUID id, Save req) {
        if (owner == null) throw new IllegalArgumentException("Your login could not be identified, so the draft cannot be saved.");
        EmployeeDraftRules.check(req.payload(), req.step());
        String label = EmployeeDraftRules.label(req.firstName(), req.lastName());
        if (id != null) {
            HrEmployeeDraft d = find(tenantId, owner, id);
            d.replace(label, req.step(), req.payload());
            return detail(drafts.save(d));
        }
        if (drafts.mine(tenantId, owner).size() >= EmployeeDraftRules.MAX_DRAFTS_PER_PERSON)
            throw new IllegalStateException("You already have " + EmployeeDraftRules.MAX_DRAFTS_PER_PERSON + " drafts. Finish or delete one first.");
        return detail(drafts.save(HrEmployeeDraft.create(tenantId, owner, label, req.step(), req.payload())));
    }

    @Transactional
    public void delete(TenantId tenantId, UUID owner, UUID id) {
        drafts.delete(find(tenantId, owner, id));
    }

    private HrEmployeeDraft find(TenantId tenantId, UUID owner, UUID id) {
        if (owner == null) throw new ResourceNotFoundException("EmployeeDraft", String.valueOf(id));
        return drafts.mineById(tenantId, owner, id).orElseThrow(() -> new ResourceNotFoundException("EmployeeDraft", String.valueOf(id)));
    }

    private static Detail detail(HrEmployeeDraft d) {
        return new Detail(d.getId(), d.getLabel(), d.getStep(), d.getPayload(), d.getUpdatedAt());
    }
}
