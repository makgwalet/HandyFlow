package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.businessreadiness.DocumentFact;
import za.co.handyflow.platform.businessreadiness.ReadinessAssessment;
import za.co.handyflow.platform.businessreadiness.RegistrationFact;
import za.co.handyflow.platform.businessreadiness.RequirementReadinessEvaluator;
import za.co.handyflow.platform.businessreadiness.RequirementRule;
import za.co.handyflow.platform.businessreadiness.RequirementToEvaluate;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceRequirement;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTender;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderRequirement;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceDocumentRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceRegistrationRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceRequirementRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRequirementRepository;
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
 * Judges a CLIENT's tender requirements against that client's registrations and documents (ADR-003). The client-side counterpart of the tenant's
 * {@code TenderReadinessService}: it only gathers this module's facts for the tender's client and maps them to the neutral records of {@code businessreadiness}. The judging
 * is the same shared, pure evaluator, so a requirement is judged identically for a tenant and for a client, and the logic is not duplicated.
 * <p>
 * A requirement is only judged against a tracked requirement that belongs to the SAME client as the tender; anything else is treated as not linked.
 */
@Service
@RequiredArgsConstructor
public class ClientTenderReadinessService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ClientTenderRepository tenderRepository;
    private final ClientTenderRequirementRepository requirementRepository;
    private final ClientComplianceRequirementRepository catalogueRepository;
    private final ClientComplianceRegistrationRepository registrationRepository;
    private final ClientComplianceDocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public ReadinessAssessment assess(TenantId tenantId, UUID clientTenderId) {
        return assess(tenantId, clientTenderId, LocalDate.now(SAST));
    }

    /** As above with the date given, so it can be tested without depending on today. */
    ReadinessAssessment assess(TenantId tenantId, UUID clientTenderId, LocalDate today) {
        ClientTender tender = tenderRepository.findByIdForTenant(tenantId, clientTenderId)
                .orElseThrow(() -> new ResourceNotFoundException("ClientTender", clientTenderId.toString()));
        UUID clientId = tender.getClientId();
        LocalDate asOf = tender.getClosingDate() != null ? tender.getClosingDate() : today;
        String basis = tender.getClosingDate() != null ? "CLOSING_DATE" : "TODAY";

        Map<UUID, ClientComplianceRequirement> catalogue = new HashMap<>();
        Map<String, Integer> latestVersion = new HashMap<>();
        List<RequirementToEvaluate> toEvaluate = new ArrayList<>();
        for (ClientTenderRequirement r : requirementRepository.findByTender(tenantId, clientTenderId)) {
            ClientComplianceRequirement entry = null;
            if (r.getClientRequirementId() != null) {
                if (!catalogue.containsKey(r.getClientRequirementId())) {
                    ClientComplianceRequirement found = catalogueRepository.findByIdForTenant(tenantId, r.getClientRequirementId()).orElse(null);
                    catalogue.put(r.getClientRequirementId(), found != null && clientId.equals(found.getClientId()) ? found : null);     // only this client's own requirements
                }
                entry = catalogue.get(r.getClientRequirementId());
            }
            RequirementRule rule = entry == null ? null : new RequirementRule(entry.getSatisfiedByAuthority(), entry.getSatisfiedByRegistrationType(), entry.getEvidenceType());
            boolean newer = false;
            if (entry != null) {
                final ClientComplianceRequirement linked = entry;      // effectively final, for the lambda below
                Integer latest = latestVersion.computeIfAbsent(linked.getCode(), c -> catalogueRepository.findLatestByCode(tenantId, clientId, c).map(ClientComplianceRequirement::getRequirementVersion).orElse(linked.getRequirementVersion()));
                newer = latest > linked.getRequirementVersion();
            }
            toEvaluate.add(new RequirementToEvaluate(entry == null ? null : r.getClientRequirementId(), r.getDescription(), r.getStatus(), rule, newer));
        }

        List<RegistrationFact> registrations = registrationRepository.findByClient(tenantId, clientId).stream()
                .map(g -> new RegistrationFact(g.getAuthority(), g.getRegistrationType(), g.getStatus(), g.getExpiryDate())).toList();
        List<DocumentFact> documents = documentRepository.findByClient(tenantId, clientId).stream()
                .map(d -> new DocumentFact(d.getDocumentType(), d.getExpiryDate(), d.isVerified())).toList();

        return RequirementReadinessEvaluator.evaluate(today, asOf, basis, toEvaluate, registrations, documents);
    }
}
