package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityAsset;
import za.co.handyflow.platform.facilities.domain.model.FacilityComplianceCertificate;
import za.co.handyflow.platform.facilities.domain.repository.FacilityAssetRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityComplianceCertificateRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySiteRepository;
import za.co.handyflow.platform.facilities.dto.ComplianceCertificateResponse;
import za.co.handyflow.platform.facilities.dto.CreateComplianceCertificateRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Chunk 3: certificates are limited to the caller's site scope; issuing and
 * revoking are audited (a revoked electrical COC or fire certificate has
 * real insurance and legal consequences, so who revoked it, when and why is
 * recorded). Asset-belongs-to-site validation from Chunk 1 is retained.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityComplianceCertificateService {

    private final FacilityComplianceCertificateRepository certificateRepository;
    private final FacilitySiteRepository siteRepository;
    private final FacilityAssetRepository assetRepository;
    private final FacilityAccessPolicy accessPolicy;
    private final FacilityAuditService auditService;

    @Transactional(readOnly = true)
    public Page<ComplianceCertificateResponse> getCertificates(TenantId tenantId, UUID siteId, Pageable pageable) {
        AccessScope scope = accessPolicy.scope(tenantId);
        if (siteId != null) scope.requireSite(siteId);
        return certificateRepository.findAllInScope(tenantId, siteId, scope.allSites(), scope.siteIds(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ComplianceCertificateResponse getCertificate(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id, accessPolicy.scope(tenantId)));
    }

    @Transactional
    public ComplianceCertificateResponse issue(TenantId tenantId, CreateComplianceCertificateRequest req) {
        accessPolicy.scope(tenantId).requireSite(req.siteId());
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FacilitySite", req.siteId().toString()));

        if (req.assetId() != null) {
            FacilityAsset asset = assetRepository.findActiveById(tenantId, req.assetId())
                    .orElseThrow(() -> new ResourceNotFoundException("FacilityAsset", req.assetId().toString()));
            if (!asset.getSiteId().equals(req.siteId())) {
                throw new IllegalArgumentException("The selected asset does not belong to the selected site");
            }
        }

        FacilityComplianceCertificate cert = FacilityComplianceCertificate.create(tenantId, req.siteId(),
                req.assetId(), req.certificateType(), req.certificateNumber(), req.issuedBy(),
                req.issueDate(), req.expiryDate(), req.documentRef());
        certificateRepository.save(cert);
        auditService.record(tenantId, "CERTIFICATE_ISSUED", "CERTIFICATE", cert.getId(),
                "type=" + cert.getCertificateType() + " number=" + cert.getCertificateNumber()
                        + " expires=" + cert.getExpiryDate() + " site=" + cert.getSiteId());
        log.info("Compliance certificate issued id={} type={} tenant={}", cert.getId(), cert.getCertificateType(), tenantId);
        return toResponse(cert);
    }

    @Transactional
    public ComplianceCertificateResponse revoke(TenantId tenantId, UUID id, String reason) {
        FacilityComplianceCertificate cert = find(tenantId, id, accessPolicy.scope(tenantId));
        cert.revoke(reason);
        certificateRepository.save(cert);
        auditService.record(tenantId, "CERTIFICATE_REVOKED", "CERTIFICATE", id,
                "type=" + cert.getCertificateType() + " number=" + cert.getCertificateNumber() + " reason=" + reason);
        log.info("Compliance certificate revoked id={} tenant={}", id, tenantId);
        return toResponse(cert);
    }

    private FacilityComplianceCertificate find(TenantId tenantId, UUID id, AccessScope scope) {
        FacilityComplianceCertificate cert = certificateRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityComplianceCertificate", id.toString()));
        if (!scope.canSeeSite(cert.getSiteId())) {
            throw new ResourceNotFoundException("FacilityComplianceCertificate", id.toString());
        }
        return cert;
    }

    private ComplianceCertificateResponse toResponse(FacilityComplianceCertificate c) {
        return new ComplianceCertificateResponse(c.getId(), c.getSiteId(), c.getAssetId(), c.getCertificateType(),
                c.getCertificateNumber(), c.getIssuedBy(), c.getIssueDate(), c.getExpiryDate(),
                c.getDocumentRef(), c.getStatus(), c.getRevokedReason(), c.getCreatedAt());
    }
}