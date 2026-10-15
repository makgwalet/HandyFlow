package za.co.handyflow.platform.controls.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.controls.domain.model.ControlException;
import za.co.handyflow.platform.controls.domain.repository.ControlExceptionRepository;
import za.co.handyflow.platform.controls.dto.ControlExceptionResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Stage 1, Option C — see ControlExceptionFacade's own Javadoc for the
 * full rationale. This is deliberately a thin, generic layer: no
 * enforcement, no blocking behaviour, purely observe-and-record. The
 * plan's own "detect-only, not enforcement" line for Stage 1 means this
 * class should never grow a method that stops another module's action
 * from happening — only ever records that something was flagged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlExceptionService implements ControlExceptionFacade {

    private final ControlExceptionRepository repo;

    @Override
    @Transactional
    public ControlExceptionResponse raise(TenantId tenantId, String sourceModule, String controlType,
                                          String relatedEntityType, UUID relatedEntityId,
                                          String severity, String description) {
        ControlException exception = ControlException.raise(tenantId.getValue(), sourceModule, controlType,
                relatedEntityType, relatedEntityId, severity, description);
        repo.save(exception);
        log.info("Control exception raised: type={} source={} entity={}:{} tenant={}",
                controlType, sourceModule, relatedEntityType, relatedEntityId, tenantId.getValue());
        return toResponse(exception);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ControlExceptionResponse> listOpen(TenantId tenantId) {
        return repo.findOpenByTenant(tenantId.getValue()).stream().map(this::toResponse).toList();
    }

    // NEW: Stage 3 — see ControlExceptionFacade's own Javadoc.
    @Override
    @Transactional(readOnly = true)
    public List<ControlExceptionResponse> listAll(TenantId tenantId) {
        return repo.findAllByTenant(tenantId.getValue()).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ControlExceptionResponse> listByIds(TenantId tenantId, java.util.Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return repo.findAllByTenant(tenantId.getValue()).stream()
                .filter(e -> ids.contains(e.getId())).map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void resolveForEntity(TenantId tenantId, String relatedEntityType, UUID relatedEntityId,
                                 UUID resolvedBy, String resolvedByName, String resolutionNotes) {
        List<ControlException> open = repo.findOpenForEntity(tenantId.getValue(), relatedEntityType, relatedEntityId);
        for (ControlException e : open) {
            e.resolveBySystem(resolvedBy, resolvedByName, resolutionNotes);
            repo.save(e);
        }
        if (!open.isEmpty()) {
            log.info("Resolved {} open control exception(s) for {}:{} tenant={}",
                    open.size(), relatedEntityType, relatedEntityId, tenantId.getValue());
        }
    }

    private ControlExceptionResponse toResponse(ControlException e) {
        return new ControlExceptionResponse(e.getId(), e.getSourceModule(), e.getControlType(),
                e.getRelatedEntityType(), e.getRelatedEntityId(), e.getSeverity(), e.getDescription(),
                e.getStatus(), e.getDetectedAt(), e.getResolvedByName(), e.getResolvedAt(), e.getResolutionNotes());
    }
}