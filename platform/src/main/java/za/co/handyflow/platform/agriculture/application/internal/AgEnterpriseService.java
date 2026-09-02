package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgEnterprise;
import za.co.handyflow.platform.agriculture.domain.repository.AgEnterpriseRepository;
import za.co.handyflow.platform.agriculture.dto.CreateEnterpriseRequest;
import za.co.handyflow.platform.agriculture.dto.EnterpriseResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateEnterpriseRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgEnterpriseService {

    private final AgEnterpriseRepository enterpriseRepository;

    @Transactional(readOnly = true)
    public Page<EnterpriseResponse> getEnterprisesForFarm(TenantId tenantId, UUID farmId, Pageable pageable) {
        return enterpriseRepository.findAllActiveForFarm(tenantId, farmId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EnterpriseResponse getEnterprise(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional
    public EnterpriseResponse createEnterprise(TenantId tenantId, CreateEnterpriseRequest req) {
        AgEnterprise enterprise = AgEnterprise.create(tenantId, req.farmId(), req.name(), req.enterpriseType(),
                req.speciesFocus(), req.startDate());
        enterpriseRepository.save(enterprise);
        log.info("Enterprise created id={} farm={} tenant={}", enterprise.getId(), req.farmId(), tenantId.getValue());
        return toResponse(enterprise);
    }

    @Transactional
    public EnterpriseResponse updateEnterprise(TenantId tenantId, UUID id, UpdateEnterpriseRequest req) {
        AgEnterprise enterprise = findActive(tenantId, id);
        enterprise.update(req.name(), req.speciesFocus(), req.notes());
        return toResponse(enterprise);
    }

    @Transactional
    public EnterpriseResponse deactivateEnterprise(TenantId tenantId, UUID id) {
        AgEnterprise enterprise = findActive(tenantId, id);
        enterprise.deactivate();
        return toResponse(enterprise);
    }

    @Transactional
    public EnterpriseResponse reactivateEnterprise(TenantId tenantId, UUID id) {
        AgEnterprise enterprise = findActive(tenantId, id);
        enterprise.reactivate();
        return toResponse(enterprise);
    }

    @Transactional
    public void deleteEnterprise(TenantId tenantId, UUID id) {
        AgEnterprise enterprise = findActive(tenantId, id);
        enterprise.softDelete();
        log.info("Enterprise deleted id={} tenant={}", id, tenantId.getValue());
    }

    private AgEnterprise findActive(TenantId tenantId, UUID id) {
        return enterpriseRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Enterprise", id.toString()));
    }

    private EnterpriseResponse toResponse(AgEnterprise e) {
        return new EnterpriseResponse(
                e.getId(), e.getFarmId(), e.getName(), e.getEnterpriseType(), e.getSpeciesFocus(),
                e.getStartDate(), e.getStatus(), e.getNotes(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
