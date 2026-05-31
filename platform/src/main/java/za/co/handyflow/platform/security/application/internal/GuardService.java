// security/application/internal/GuardService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuardService {

    private final GuardRepository guardRepository;

    @Transactional(readOnly = true)
    public Page<GuardResponse> getGuards(TenantId tenantId, String search, Pageable pageable) {
        var page = (search == null || search.isBlank())
                ? guardRepository.findAllActive(tenantId, pageable)
                : guardRepository.searchActive(tenantId, search, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public GuardResponse getGuard(TenantId tenantId, UUID id) {
        return guardRepository.findActiveById(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", id.toString()));
    }

    @Transactional
    public GuardResponse createGuard(TenantId tenantId, CreateGuardRequest req) {
        if (req.psiraNumber() != null &&
                guardRepository.existsByTenantIdAndPsiraNumberAndDeletedAtIsNull(
                        tenantId, req.psiraNumber())) {
            throw new IllegalArgumentException(
                    "A guard with PSiRA number " + req.psiraNumber() + " already exists"
            );
        }
        Guard guard = Guard.create(tenantId, req.firstName(), req.lastName(),
                req.psiraNumber(), req.idNumber(), req.phone(), req.grade());
        guardRepository.save(guard);
        log.info("Created guard={} tenant={}", guard.getFullName(), tenantId);
        return toResponse(guard);
    }

    @Transactional
    public GuardResponse updateGuard(TenantId tenantId, UUID id, CreateGuardRequest req) {
        Guard guard = findActive(tenantId, id);
        guard.update(req.firstName(), req.lastName(), req.psiraNumber(),
                req.idNumber(), req.phone(), req.grade(), req.notes());
        guardRepository.save(guard);
        return toResponse(guard);
    }

    @Transactional
    public void deleteGuard(TenantId tenantId, UUID id) {
        Guard guard = findActive(tenantId, id);
        guard.softDelete(null);
        guardRepository.save(guard);
        log.info("Soft deleted guard={} tenant={}", id, tenantId);
    }

    @Transactional
    public GuardResponse updatePhoto(TenantId tenantId, UUID id, String photoBase64) {
        Guard guard = findActive(tenantId, id);
        guard.updatePhoto("data:image/jpeg;base64," + photoBase64);
        guardRepository.save(guard);
        return toResponse(guard);
    }

    private Guard findActive(TenantId tenantId, UUID id) {
        return guardRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", id.toString()));
    }

    private GuardResponse toResponse(Guard g) {
        return new GuardResponse(
                g.getId(), g.getFirstName(), g.getLastName(),
                g.getFullName(), g.getPsiraNumber(), g.getIdNumber(),
                g.getPhone(), g.getPhotoUrl(), g.getGrade(),
                g.isActive(), g.getNotes(), g.getCreatedAt()
        );
    }
}