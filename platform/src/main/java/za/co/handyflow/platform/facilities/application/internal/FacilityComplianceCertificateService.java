package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityComplianceCertificate;
import za.co.handyflow.platform.facilities.domain.repository.FacilityComplianceCertificateRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySiteRepository;
import za.co.handyflow.platform.facilities.dto.ComplianceCertificateResponse;
import za.co.handyflow.platform.facilities.dto.CreateComplianceCertificateRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityComplianceCertificateService {

    private final FacilityComplianceCertificateRepository certificateRepository;
    private final FacilitySiteRepository siteRepository;

    @Transactional(readOnly = true)
    public Page<ComplianceCertificateResponse> getCertificates(TenantId tenantId, UUID siteId, Pageable pageable) {
        return certificateRepository.findAll(tenantId, siteId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ComplianceCertificateResponse getCertificate(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public ComplianceCertificateResponse issue(TenantId tenantId, CreateComplianceCertificateRequest req) {
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FacilitySite", req.siteId().toString()));

        FacilityComplianceCertificate cert = FacilityComplianceCertificate.create(tenantId, req.siteId(),
                req.assetId(), req.certificateType(), req.certificateNumber(), req.issuedBy(),
                req.issueDate(), req.expiryDate(), req.documentRef());
        certificateRepository.save(cert);
        log.info("Compliance certificate issued id={} type={} tenant={}", cert.getId(), cert.getCertificateType(), tenantId);
        return toResponse(cert);
    }

    @Transactional
    public ComplianceCertificateResponse revoke(TenantId tenantId, UUID id, String reason) {
        FacilityComplianceCertificate cert = find(tenantId, id);
        cert.revoke(reason);
        certificateRepository.save(cert);
        log.info("Compliance certificate revoked id={} tenant={}", id, tenantId);
        return toResponse(cert);
    }

    private FacilityComplianceCertificate find(TenantId tenantId, UUID id) {
        return certificateRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityComplianceCertificate", id.toString()));
    }

    private ComplianceCertificateResponse toResponse(FacilityComplianceCertificate c) {
        return new ComplianceCertificateResponse(c.getId(), c.getSiteId(), c.getAssetId(), c.getCertificateType(),
                c.getCertificateNumber(), c.getIssuedBy(), c.getIssueDate(), c.getExpiryDate(),
                c.getDocumentRef(), c.getStatus(), c.getRevokedReason(), c.getCreatedAt());
    }
}
