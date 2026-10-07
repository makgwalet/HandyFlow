package za.co.handyflow.platform.compliancetender.application.internal.submission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceRequirement;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.compliancetender.domain.model.TenderRequirement;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceDocumentRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRequirementRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRequirementRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reads the tender's requirements and the business's documents, and hands them to the pure {@link DocumentSuggester}. */
@Service
@RequiredArgsConstructor
public class PackageDocumentSuggestions {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TenderRepository tenders;
    private final TenderRequirementRepository requirements;
    private final ComplianceRequirementRepository catalogue;
    private final ComplianceDocumentRepository documents;

    @Transactional(readOnly = true)
    public List<DocumentSuggester.Suggestion> forTender(TenantId tenantId, UUID tenderId) {
        Tender tender = tenders.findByIdForTenant(tenantId, tenderId).orElseThrow(() -> new ResourceNotFoundException("Tender", tenderId.toString()));
        LocalDate asOf = tender.getClosingDate() != null ? tender.getClosingDate() : LocalDate.now(SAST);
        List<DocumentSuggester.Need> needs = new ArrayList<>();
        for (TenderRequirement r : requirements.findByTender(tenantId, tenderId)) {
            if (r.getComplianceRequirementId() == null) continue;
            ComplianceRequirement entry = catalogue.findByIdForTenant(tenantId, r.getComplianceRequirementId()).orElse(null);
            if (entry != null && entry.getEvidenceType() != null && !entry.getEvidenceType().isBlank()) {
                needs.add(new DocumentSuggester.Need(r.getDescription(), entry.getEvidenceType()));
            }
        }
        List<DocumentSuggester.Candidate> candidates = documents.findAllForTenant(tenantId).stream()
                .map(d -> new DocumentSuggester.Candidate(d.getId(), d.getDocumentType(), d.getIssueDate(), d.getExpiryDate(), d.isVerified())).toList();
        return DocumentSuggester.suggest(needs, candidates, asOf);
    }
}
