package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.debtcollection.application.LegalMatterGateway;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.legalcompliance.domain.model.DebtHandoverLink;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatter;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatterType;
import za.co.handyflow.platform.legalcompliance.domain.repository.DebtHandoverLinkRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Legal/Compliance's implementation of Debt Collection's LegalMatterGateway: a reviewed handover becomes a
 * LitigationMatter (the company as CLAIMANT against the debtor), linked back by id.
 * <p>
 * IDEMPOTENT on handoverId via DebtHandoverLink — a retried transfer returns the same matter. The frozen package
 * JSON is attached to the matter as evidence (best effort, in its own transaction): the matter and link are the
 * source of truth, and the description always carries the handover id and package SHA-256 so provenance survives
 * even if the attachment step fails. Nothing is re-captured by legal staff.
 * <p>
 * Mapping: NCA agreements → CIVIL, other commercial → COMMERCIAL. estimatedExposure is the claim amount as per
 * the case snapshot at handover. legalRepresentative, court and next key date stay empty for the legal team to
 * fill in — this module has no stage field for demand/section 129/summons/judgment, only status and one key date.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebtHandoverMatterGateway implements LegalMatterGateway {

    static final String EVIDENCE_TYPE = "DEBT_COLLECTION_HANDOVER_PACKAGE";

    private final LitigationMatterService matterService;
    private final DebtHandoverLinkRepository linkRepository;
    private final EvidenceFacade evidenceFacade;
    private final TransactionTemplate tx;

    @Override
    public MatterRef createMatterFromHandover(HandoverTransfer t) {
        var existing = linkRepository.findByHandoverId(t.tenantId(), t.handoverId());
        if (existing.isPresent()) return refFor(t, existing.get().getMatterId());

        LitigationMatter matter;
        try {
            matter = tx.execute(s -> {
                LitigationMatter m = matterService.create(t.tenantId(),
                        "Debt recovery — " + t.debtorName() + " (" + t.caseNumber() + ")",
                        mapType(t.agreementType()), t.debtorName(), "CLAIMANT", t.balanceSnapshot(), null, null, null,
                        LocalDate.now(), null, describe(t), t.linkedContractId(), t.requestedBy());
                linkRepository.save(DebtHandoverLink.of(t.tenantId(), t.handoverId(), m.getId()));
                return m;
            });
        } catch (DataIntegrityViolationException e) {
            // A concurrent transfer for the same handover won the unique index: return its matter.
            var winner = linkRepository.findByHandoverId(t.tenantId(), t.handoverId());
            if (winner.isPresent()) return refFor(t, winner.get().getMatterId());
            throw e;
        }
        attachPackage(t, matter);
        return new MatterRef(matter.getId(), matter.getMatterNumber());
    }

    static LitigationMatterType mapType(String agreementType) {
        return switch (agreementType == null ? "" : agreementType) {
            case "NCA_CREDIT_AGREEMENT" -> LitigationMatterType.CIVIL;
            case "OTHER_COMMERCIAL" -> LitigationMatterType.COMMERCIAL;
            default -> LitigationMatterType.OTHER;
        };
    }

    static String describe(HandoverTransfer t) {
        return "Debt recovery handed over from Debt Collection case " + t.caseNumber() + ".\n"
                + "Debtor: " + t.debtorName() + ". Claim at handover: R"
                + (t.balanceSnapshot() != null ? t.balanceSnapshot().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "n/a")
                + ". Agreement type: " + t.agreementType() + ".\n"
                + "Source: handover " + t.handoverId() + ", case " + t.caseId() + ", package sha256 " + t.packageHash()
                + ". The full transfer package (balances, payment plans, promises, contact and communication history, "
                + "notices and review notes) is attached as evidence.";
    }

    private MatterRef refFor(HandoverTransfer t, UUID matterId) {
        LitigationMatter m = matterService.get(t.tenantId(), matterId);
        return new MatterRef(m.getId(), m.getMatterNumber());
    }

    private void attachPackage(HandoverTransfer t, LitigationMatter matter) {
        TransactionTemplate isolated = new TransactionTemplate(tx.getTransactionManager());
        isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            isolated.executeWithoutResult(s -> evidenceFacade.attach(t.tenantId(),
                    new ByteArrayMultipartFile("debt-collection-handover-" + t.caseNumber() + ".json", "application/json",
                            t.packageJson().getBytes(StandardCharsets.UTF_8)),
                    EVIDENCE_TYPE, "legalcompliance", "LitigationMatter", matter.getId(), null, t.requestedBy(),
                    "Debt Collection handover"));
        } catch (Exception e) {
            log.warn("[Legal/Compliance] matter {} created from handover {} but the package could not be attached "
                    + "as evidence (check allowed evidenceType values): {}", matter.getMatterNumber(), t.handoverId(), e.getMessage());
        }
    }
}
