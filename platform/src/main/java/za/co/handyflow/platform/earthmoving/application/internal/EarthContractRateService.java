package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.contracting.application.ContractSummary;
import za.co.handyflow.platform.contracting.application.ContractingFacade;
import za.co.handyflow.platform.earthmoving.domain.model.EarthContractRate;
import za.co.handyflow.platform.earthmoving.domain.model.EarthDeployment;
import za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules;
import za.co.handyflow.platform.earthmoving.domain.model.HireRules;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthContractRateRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthDeploymentRepository;
import za.co.handyflow.platform.earthmoving.dto.ContractRateRequest;
import za.co.handyflow.platform.earthmoving.dto.ContractRateResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The rates agreed in a hire contract: a rate per day, week, month, hour and kilometre, and the kilometres included. They are
 * held against the contract, so every deployment under it uses them. A hire charge takes its rate from here before the
 * machine's own. Changing a rate changes charges raised afterwards, never ones already raised. Administrators only.
 */
@Service
@RequiredArgsConstructor
public class EarthContractRateService {

    private final EarthDeploymentRepository deployments;
    private final EarthContractRateRepository rates;
    private final ContractingFacade contracting;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;

    @Transactional(readOnly = true)
    public ContractRateResponse forDeployment(TenantId tenantId, UUID deploymentId) {
        access.require(EarthmovingRules.READ, "view contract rates");
        return response(tenantId, deployment(tenantId, deploymentId));
    }

    @Transactional
    public ContractRateResponse set(TenantId tenantId, UUID deploymentId, ContractRateRequest req, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "set contract rates");
        EarthDeployment d = deployment(tenantId, deploymentId);
        UUID contractId = linked(d);
        String basis = HireRules.basis(req.basis());
        EarthContractRate existing = rates.findByContractAndBasis(tenantId, contractId, basis).orElse(null);
        Map<String, Object> before = existing == null ? null : snapshot(existing);
        EarthContractRate saved;
        if (existing == null) saved = rates.save(EarthContractRate.of(tenantId, contractId, basis, req.unitRate(), req.includedKm(), actor));
        else { existing.set(req.unitRate(), req.includedKm(), actor); saved = rates.save(existing); }
        trail.record(tenantId, "EARTHMOVING_CONTRACT_RATE_SET", "EARTHMOVING_DEPLOYMENT", d.getId(), before, snapshot(saved));
        return response(tenantId, d);
    }

    @Transactional
    public ContractRateResponse remove(TenantId tenantId, UUID deploymentId, String basisText, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "remove a contract rate");
        EarthDeployment d = deployment(tenantId, deploymentId);
        UUID contractId = linked(d);
        String basis = HireRules.basis(basisText);
        EarthContractRate existing = rates.findByContractAndBasis(tenantId, contractId, basis)
                .orElseThrow(() -> new IllegalArgumentException("The contract has no " + HireRules.word(basis) + " rate."));
        rates.delete(existing);
        trail.record(tenantId, "EARTHMOVING_CONTRACT_RATE_REMOVED", "EARTHMOVING_DEPLOYMENT", d.getId(), snapshot(existing), null);
        return response(tenantId, d);
    }

    private static UUID linked(EarthDeployment d) {
        if (d.getContractId() == null) throw new IllegalStateException("This deployment has no hire contract. Make or link the contract first.");
        return d.getContractId();
    }

    private ContractRateResponse response(TenantId tenantId, EarthDeployment d) {
        if (d.getContractId() == null) return new ContractRateResponse(d.getId(), null, null, java.util.List.of());
        String number = contracting.findById(tenantId, d.getContractId()).map(ContractSummary::contractNumber).orElse(null);
        return new ContractRateResponse(d.getId(), d.getContractId(), number, rates.findByContract(tenantId, d.getContractId()).stream()
                .map(r -> new ContractRateResponse.Rate(r.getBasis(), r.getUnitRate(), r.getIncludedKm(), r.getUpdatedAt())).toList());
    }

    private EarthDeployment deployment(TenantId tenantId, UUID id) {
        return deployments.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Deployment", id.toString()));
    }

    private static Map<String, Object> snapshot(EarthContractRate r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contractId", String.valueOf(r.getContractId()));
        m.put("basis", r.getBasis());
        m.put("rate", r.getUnitRate().toPlainString());
        if (r.getIncludedKm() != null) m.put("includedKm", r.getIncludedKm().toPlainString());
        return m;
    }
}
