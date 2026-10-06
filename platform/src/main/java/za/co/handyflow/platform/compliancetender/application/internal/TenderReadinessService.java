package za.co.handyflow.platform.compliancetender.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.businessreadiness.DocumentFact;
import za.co.handyflow.platform.businessreadiness.ReadinessAssessment;
import za.co.handyflow.platform.businessreadiness.RegistrationFact;
import za.co.handyflow.platform.businessreadiness.RequirementReadinessEvaluator;
import za.co.handyflow.platform.businessreadiness.RequirementRule;
import za.co.handyflow.platform.businessreadiness.RequirementToEvaluate;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceRequirement;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.compliancetender.domain.model.TenderRequirement;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceDocumentRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRegistrationRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRequirementRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRequirementRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Judges a tender's requirements against the business's own registrations and documents (ADR-003). Read-only: it never changes a requirement's status, the user's own tick
 * stays theirs, and the assessment only reports where the evidence disagrees.
 * <p>
 * This class only gathers facts and maps this module's entities to the neutral records of {@code businessreadiness}; the judging itself is the shared, pure
 * {@link RequirementReadinessEvaluator}, so the tenant side and the client side (complianceservices) apply identical rules.
 * <p>
 * A requirement is judged against the tracked-requirement VERSION it was linked to (see {@link ComplianceRequirement#newVersion}: a check is judged against the version in
 * force, not silently reinterpreted), and says when a newer version exists.
 */
@Service
@RequiredArgsConstructor
public class TenderReadinessService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TenderRepository tenderRepository;
    private final TenderRequirementRepository requirementRepository;
    private final ComplianceRequirementRepository catalogueRepository;
    private final ComplianceRegistrationRepository registrationRepository;
    private final ComplianceDocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public ReadinessAssessment assess(TenantId tenantId, UUID tenderId) {
        return assess(tenantId, tenderId, LocalDate.now(SAST));
    }

    /** As above with the date given, so it can be tested without depending on today. */
    ReadinessAssessment assess(TenantId tenantId, UUID tenderId, LocalDate today) {
        Tender tender = tenderRepository.findByIdForTenant(tenantId, tenderId)
                .orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));
        // a certificate that expires the week before closing is not valid for this tender, so it is judged on the closing date; with no date set, today
        LocalDate asOf = tender.getClosingDate() != null ? tender.getClosingDate() : today;
        String basis = tender.getClosingDate() != null ? "CLOSING_DATE" : "TODAY";

        Map<UUID, ComplianceRequirement> catalogue = new HashMap<>();
        Map<String, Integer> latestVersion = new HashMap<>();
        List<RequirementToEvaluate> toEvaluate = new ArrayList<>();
        for (TenderRequirement r : requirementRepository.findByTender(tenantId, tenderId)) {
            ComplianceRequirement entry = null;
            if (r.getComplianceRequirementId() != null) {
                if (!catalogue.containsKey(r.getComplianceRequirementId())) {
                    catalogue.put(r.getComplianceRequirementId(), catalogueRepository.findByIdForTenant(tenantId, r.getComplianceRequirementId()).orElse(null));
                }
                entry = catalogue.get(r.getComplianceRequirementId());
            }
            RequirementRule rule = entry == null ? null : new RequirementRule(entry.getSatisfiedByAuthority(), entry.getSatisfiedByRegistrationType(), entry.getEvidenceType());
            boolean newer = false;
            if (entry != null) {
                final ComplianceRequirement linked = entry;      // effectively final, for the lambda below
                Integer latest = latestVersion.computeIfAbsent(linked.getCode(), c -> catalogueRepository.findLatestByCode(tenantId, c).map(ComplianceRequirement::getRequirementVersion).orElse(linked.getRequirementVersion()));
                newer = latest > linked.getRequirementVersion();
            }
            // an id that no longer resolves (or never belonged to this tenant) is treated as not linked, so nothing is judged against someone else's rule
            toEvaluate.add(new RequirementToEvaluate(entry == null ? null : r.getComplianceRequirementId(), r.getDescription(), r.getStatus(), rule, newer));
        }

        List<RegistrationFact> registrations = registrationRepository.findAllForTenant(tenantId).stream()
                .map(g -> new RegistrationFact(g.getAuthority(), g.getRegistrationType(), g.getStatus(), g.getExpiryDate())).toList();
        List<DocumentFact> documents = documentRepository.findAllForTenant(tenantId).stream()
                .map(d -> new DocumentFact(d.getDocumentType(), d.getExpiryDate(), d.isVerified())).toList();

        return RequirementReadinessEvaluator.evaluate(today, asOf, basis, toEvaluate, registrations, documents);
    }
}
