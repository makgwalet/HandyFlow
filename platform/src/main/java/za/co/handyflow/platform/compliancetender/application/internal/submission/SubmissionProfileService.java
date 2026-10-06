package za.co.handyflow.platform.compliancetender.application.internal.submission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.domain.model.TenderSubmissionProfile;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderSubmissionProfileRepository;
import za.co.handyflow.platform.compliancetender.dto.SubmissionProfileRequest;
import za.co.handyflow.platform.compliancetender.dto.SubmissionProfileResponse;
import za.co.handyflow.platform.shared.BusinessException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Saved, named submission profiles for a tenant (ADR-005 decision 7). */
@Service
@RequiredArgsConstructor
public class SubmissionProfileService {

    private final TenderSubmissionProfileRepository repository;

    @Transactional(readOnly = true)
    public List<SubmissionProfileResponse> list(TenantId tenantId) {
        return repository.findAllForTenant(tenantId).stream().map(SubmissionProfileService::toResponse).toList();
    }

    /** The domain profile for a saved one, or null when none is chosen. A chosen id that is not this tenant's is a 404, never silently ignored. */
    @Transactional(readOnly = true)
    public SubmissionProfile load(TenantId tenantId, UUID id) {
        if (id == null) return null;
        TenderSubmissionProfile p = repository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("SubmissionProfile", id.toString()));
        return toDomain(p);
    }

    @Transactional
    public SubmissionProfileResponse create(TenantId tenantId, SubmissionProfileRequest r, UUID userId) {
        requireFreeName(tenantId, r.name(), null);
        TenderSubmissionProfile p = TenderSubmissionProfile.create(tenantId, r.name(), r.allowedExtensions(), r.maxFileBytes(),
                r.maxTotalBytes(), r.maxFileCount(), r.zipAllowed(), r.maxFileNameLength(), userId);
        return toResponse(repository.save(p));
    }

    @Transactional
    public SubmissionProfileResponse update(TenantId tenantId, UUID id, SubmissionProfileRequest r, UUID userId) {
        TenderSubmissionProfile p = repository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("SubmissionProfile", id.toString()));
        requireFreeName(tenantId, r.name(), id);
        p.apply(r.name(), r.allowedExtensions(), r.maxFileBytes(), r.maxTotalBytes(), r.maxFileCount(), r.zipAllowed(), r.maxFileNameLength(), userId);
        return toResponse(p);
    }

    /** Packages already built keep their own frozen copy of the profile, so deleting a profile changes none of them. */
    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        TenderSubmissionProfile p = repository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("SubmissionProfile", id.toString()));
        repository.delete(p);
    }

    private void requireFreeName(TenantId tenantId, String name, UUID excludeId) {
        if (name != null && repository.nameTaken(tenantId, name.trim(), excludeId)) {
            throw new BusinessException("A submission profile called \"" + name.trim() + "\" already exists.");
        }
    }

    static SubmissionProfile toDomain(TenderSubmissionProfile p) {
        return new SubmissionProfile(p.getName(), p.extensionSet(), p.getMaxFileBytes(), p.getMaxTotalBytes(), p.getMaxFileCount(),
                p.getZipAllowed(), p.getMaxFileNameLength());
    }

    private static SubmissionProfileResponse toResponse(TenderSubmissionProfile p) {
        return new SubmissionProfileResponse(p.getId(), p.getName(), p.extensionSet(), p.getMaxFileBytes(), p.getMaxTotalBytes(),
                p.getMaxFileCount(), p.getZipAllowed(), p.getMaxFileNameLength());
    }
}
