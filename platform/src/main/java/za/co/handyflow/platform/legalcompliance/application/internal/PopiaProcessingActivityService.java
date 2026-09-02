package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.DataCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.LawfulBasis;
import za.co.handyflow.platform.legalcompliance.domain.model.PopiaProcessingActivity;
import za.co.handyflow.platform.legalcompliance.domain.repository.PopiaProcessingActivityRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for the org-wide POPIA processing-activity register — see
 * PopiaProcessingActivity's own class Javadoc for how this differs in scope
 * from crm.CustomerConsent (per-customer consent vs. this register-level
 * "what do we process, why, on what basis" record).
 */
@Service
@RequiredArgsConstructor
public class PopiaProcessingActivityService {

    private final PopiaProcessingActivityRepository repository;

    @Transactional
    public PopiaProcessingActivity create(TenantId tenantId, String activityName, DataCategory dataCategory,
                                           String purpose, LawfulBasis lawfulBasis, String responsibleDepartment,
                                           UUID responsibleUserId, String responsibleUserName,
                                           String retentionPeriodDescription, boolean crossBorderTransfer,
                                           String crossBorderDetails, String securityMeasures,
                                           LocalDate reviewDate, UUID createdBy) {
        PopiaProcessingActivity activity = PopiaProcessingActivity.create(tenantId, activityName, dataCategory,
                purpose, lawfulBasis, responsibleDepartment, responsibleUserId, responsibleUserName,
                retentionPeriodDescription, crossBorderTransfer, crossBorderDetails, securityMeasures,
                reviewDate, createdBy);
        return repository.save(activity);
    }

    @Transactional(readOnly = true)
    public List<PopiaProcessingActivity> list(TenantId tenantId) {
        return repository.findAllActive(tenantId);
    }

    @Transactional(readOnly = true)
    public PopiaProcessingActivity get(TenantId tenantId, UUID id) {
        return findActive(tenantId, id);
    }

    @Transactional
    public PopiaProcessingActivity update(TenantId tenantId, UUID id, String activityName,
                                           DataCategory dataCategory, String purpose, LawfulBasis lawfulBasis,
                                           String responsibleDepartment, UUID responsibleUserId,
                                           String responsibleUserName, String retentionPeriodDescription,
                                           boolean crossBorderTransfer, String crossBorderDetails,
                                           String securityMeasures, LocalDate reviewDate) {
        PopiaProcessingActivity activity = findActive(tenantId, id);
        activity.update(activityName, dataCategory, purpose, lawfulBasis, responsibleDepartment, responsibleUserId,
                responsibleUserName, retentionPeriodDescription, crossBorderTransfer, crossBorderDetails,
                securityMeasures, reviewDate);
        return repository.save(activity);
    }

    @Transactional
    public PopiaProcessingActivity deactivate(TenantId tenantId, UUID id) {
        PopiaProcessingActivity activity = findActive(tenantId, id);
        activity.deactivate();
        return repository.save(activity);
    }

    @Transactional
    public PopiaProcessingActivity reactivate(TenantId tenantId, UUID id) {
        PopiaProcessingActivity activity = findActive(tenantId, id);
        activity.reactivate();
        return repository.save(activity);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id, UUID deletedBy) {
        PopiaProcessingActivity activity = findActive(tenantId, id);
        activity.softDelete(deletedBy);
        repository.save(activity);
    }

    private PopiaProcessingActivity findActive(TenantId tenantId, UUID id) {
        return repository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("PopiaProcessingActivity", id.toString()));
    }
}
