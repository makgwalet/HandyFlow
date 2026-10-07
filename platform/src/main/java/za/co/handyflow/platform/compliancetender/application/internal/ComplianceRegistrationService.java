package za.co.handyflow.platform.compliancetender.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceRegistration;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRegistrationRepository;
import za.co.handyflow.platform.compliancetender.dto.ComplianceRegistrationResponse;
import za.co.handyflow.platform.compliancetender.dto.CreateComplianceRegistrationRequest;
import za.co.handyflow.platform.compliancetender.dto.UpdateComplianceRegistrationRequest;
import za.co.handyflow.platform.shared.BusinessException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Phase 1 scope only — see the strategic roadmap backlog, Part 6. This
 * class deliberately does not yet reference Projects/HR/Fleet/Accounting/
 * Supply Chain (tender cross-referencing, a later phase) or
 * complianceservices (the external client-management layer, a separate
 * module built on top of this one, not yet started).
 * <p>
 * EXPIRING_SOON_DAYS mirrors the same 30-day window used elsewhere in this
 * codebase for compliance-style expiry warnings (e.g.
 * FacilityComplianceCertificate's own scheduler) — not a magic number
 * invented fresh for this module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceRegistrationService {

    private static final int EXPIRING_SOON_DAYS = 30;

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ComplianceRegistrationRepository registrationRepository;

    @Transactional(readOnly = true)
    public Page<ComplianceRegistrationResponse> getRegistrations(TenantId tenantId, String authority, Pageable pageable) {
        return registrationRepository.findAll(tenantId, authority, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ComplianceRegistrationResponse> getAllRegistrations(TenantId tenantId) {
        return registrationRepository.findAllForTenant(tenantId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ComplianceRegistrationResponse getRegistration(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public ComplianceRegistrationResponse create(TenantId tenantId, CreateComplianceRegistrationRequest req, UUID createdBy) {
        ComplianceRegistration registration = ComplianceRegistration.create(tenantId, req.authority(), req.registrationType(),
                req.registrationNumber(), req.issuedDate(), req.expiryDate(), req.notes(), createdBy);
        // A registration entered with an expiry already in the past is recorded as what it is, never as Active.
        if (isPast(req.expiryDate())) registration.markExpired();
        registrationRepository.save(registration);
        log.info("Compliance registration created id={} authority={} type={} tenant={}",
                registration.getId(), registration.getAuthority(), registration.getRegistrationType(), tenantId);
        return toResponse(registration);
    }

    @Transactional
    public ComplianceRegistrationResponse update(TenantId tenantId, UUID id, UpdateComplianceRegistrationRequest req, UUID updatedBy) {
        ComplianceRegistration registration = find(tenantId, id);
        if ("ACTIVE".equals(req.status()) && isPast(req.expiryDate()))
            throw new BusinessException("A registration that expired on " + req.expiryDate()
                    + " cannot be Active. Set the status to Expired, or enter the renewed expiry date.");
        registration.update(req.registrationNumber(), req.status(), req.issuedDate(), req.expiryDate(), req.notes(), updatedBy);
        registrationRepository.save(registration);
        log.info("Compliance registration updated id={} tenant={}", id, tenantId);
        return toResponse(registration);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        ComplianceRegistration registration = find(tenantId, id);
        registrationRepository.delete(registration);
        log.info("Compliance registration deleted id={} tenant={}", id, tenantId);
    }

    /** True when the date is before today in South Africa (the day the registration lapses is still valid). */
    private static boolean isPast(LocalDate expiry) {
        return expiry != null && expiry.isBefore(LocalDate.now(SAST));
    }

    private ComplianceRegistration find(TenantId tenantId, UUID id) {
        return registrationRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceRegistration", id.toString()));
    }

    private ComplianceRegistrationResponse toResponse(ComplianceRegistration r) {
        return new ComplianceRegistrationResponse(r.getId(), r.getAuthority(), r.getRegistrationType(),
                r.getRegistrationNumber(), r.getStatus(), r.getIssuedDate(), r.getExpiryDate(), r.getNotes(),
                r.isExpiringWithin(EXPIRING_SOON_DAYS), r.getCreatedAt());
    }
}
