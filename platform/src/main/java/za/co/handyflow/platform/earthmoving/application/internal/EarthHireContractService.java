package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.contracting.application.ContractSummary;
import za.co.handyflow.platform.contracting.application.ContractingFacade;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthDeploymentRepository;
import za.co.handyflow.platform.earthmoving.dto.DeploymentContractResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * The link between a hire deployment and its contract. The agreement itself lives in Contracting (text, parties, signing);
 * Earthmoving keeps only the reference. A draft hire agreement can be made from the deployment, an existing contract can be
 * linked, and an extension is recorded as an addendum contract under the original. Nothing here sends or signs a contract.
 */
@Service
@RequiredArgsConstructor
public class EarthHireContractService {

    private final EarthDeploymentRepository deployments;
    private final EarthAssetRepository assets;
    private final ContractingFacade contracting;
    private final CrmFacade crm;
    private final EarthSettingsService settings;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;

    @Transactional(readOnly = true)
    public DeploymentContractResponse forDeployment(TenantId tenantId, UUID deploymentId) {
        access.require(EarthmovingRules.READ, "view the hire contract");
        EarthDeployment d = deployment(tenantId, deploymentId);
        return response(tenantId, d, asset(tenantId, d));
    }

    /** Makes a draft hire agreement for this deployment, filled with what is known, and links it. The text is completed and sent in Contracting. */
    @Transactional
    public DeploymentContractResponse createDraft(TenantId tenantId, UUID deploymentId, UUID actor) {
        access.require(EarthmovingRules.DEPLOY, "make a hire contract");
        EarthDeployment d = deployment(tenantId, deploymentId);
        EarthAsset a = asset(tenantId, d);
        if (d.getContractId() != null) throw new IllegalStateException("This deployment already has a contract.");
        if (d.getCustomerId() == null) throw new IllegalArgumentException("Link this deployment to a customer first (Sites > Links to review).");
        String hirer = crm.findCustomerById(tenantId, d.getCustomerId()).map(CustomerSummary::name).orElse(d.getClientName());

        Map<String, String> vars = new LinkedHashMap<>();
        put(vars, "hirer_name", hirer);
        put(vars, "equipment_description", describe(a));
        put(vars, "serial_number", a.getSerialNumber());
        put(vars, "hire_start_date", d.getPlannedStartDate() == null ? null : d.getPlannedStartDate().toString());
        put(vars, "hire_end_date", d.getPlannedEndDate() == null ? null : d.getPlannedEndDate().toString());
        BigDecimal value = ContractRules.expectedValue(a.getDailyRate(), d.getPlannedStartDate(), d.getPlannedEndDate());
        put(vars, "daily_rate", a.getDailyRate() == null ? null : a.getDailyRate().toPlainString());
        put(vars, "total_amount", value == null ? null : value.toPlainString());
        put(vars, "site_location", d.getSiteName());

        ContractSummary made = contracting.createDraft(tenantId, new ContractingFacade.DraftContract(
                "Equipment hire: " + describe(a) + (hirer == null ? "" : " to " + hirer), ContractRules.HIRE_TYPE, vars, null, value,
                d.getPlannedStartDate(), d.getPlannedEndDate(), null, ContractRules.REF_TYPE, d.getId(), null), actor);
        d.linkContract(made.id());
        deployments.save(d);
        audit(tenantId, "EARTHMOVING_HIRE_CONTRACT_DRAFTED", d, made, actor);
        return response(tenantId, d, a);
    }

    /** Links a contract that already exists in Contracting. */
    @Transactional
    public DeploymentContractResponse link(TenantId tenantId, UUID deploymentId, UUID contractId, UUID actor) {
        access.require(EarthmovingRules.DEPLOY, "link a hire contract");
        EarthDeployment d = deployment(tenantId, deploymentId);
        ContractSummary c = contracting.findById(tenantId, contractId).orElseThrow(() -> new IllegalArgumentException("That contract was not found."));
        d.linkContract(c.id());
        deployments.save(d);
        audit(tenantId, "EARTHMOVING_HIRE_CONTRACT_LINKED", d, c, actor);
        return response(tenantId, d, asset(tenantId, d));
    }

    /** Records an extension as a draft addendum under the deployment's contract. The planned end date is not changed here. */
    @Transactional
    public DeploymentContractResponse createAddendum(TenantId tenantId, UUID deploymentId, LocalDate newEnd, String reason, UUID actor) {
        access.require(EarthmovingRules.DEPLOY, "extend a hire");
        EarthDeployment d = deployment(tenantId, deploymentId);
        EarthAsset a = asset(tenantId, d);
        if (d.getContractId() == null) throw new IllegalStateException("There is no contract to extend. Make or link the hire contract first.");
        if (!d.isOpen()) throw new IllegalStateException("This deployment has ended, so it cannot be extended.");
        String why = EarthmovingRules.requireReason(reason, "the extension");
        LocalDate current = d.getPlannedEndDate();
        if (newEnd == null || (current != null && !newEnd.isAfter(current)) || (current == null && d.getPlannedStartDate() != null && newEnd.isBefore(d.getPlannedStartDate())))
            throw new IllegalArgumentException("The new end date must be after the current end date.");
        ContractSummary original = contracting.findById(tenantId, d.getContractId())
                .orElseThrow(() -> new IllegalStateException("The linked contract can no longer be found."));
        ContractSummary made = contracting.createDraft(tenantId, new ContractingFacade.DraftContract(
                "Addendum: extend " + describe(a) + " to " + newEnd + " (" + original.contractNumber() + ")", ContractRules.ADDENDUM_TYPE, null,
                ContractRules.addendumBody(original.contractNumber(), describe(a), current, newEnd, why), null,
                current, newEnd, why, ContractRules.REF_TYPE, d.getId(), original.id()), actor);
        audit(tenantId, "EARTHMOVING_HIRE_EXTENSION_DRAFTED", d, made, actor);
        return response(tenantId, d, a);
    }

    private DeploymentContractResponse response(TenantId tenantId, EarthDeployment d, EarthAsset a) {
        List<ContractSummary> made = contracting.findByReference(tenantId, ContractRules.REF_TYPE, d.getId());
        ContractSummary original = d.getContractId() == null ? null
                : made.stream().filter(c -> c.id().equals(d.getContractId())).findFirst()
                        .orElseGet(() -> contracting.findById(tenantId, d.getContractId()).orElse(null));
        List<DeploymentContractResponse.Contract> addenda = made.stream()
                .filter(c -> c.parentContractId() != null).map(EarthHireContractService::view).toList();
        BigDecimal value = ContractRules.expectedValue(a.getDailyRate(), d.getPlannedStartDate(), d.getPlannedEndDate());
        BigDecimal threshold = settings.contractThreshold(tenantId);
        boolean expected = d.getCustomerId() != null && ContractRules.isExpected(threshold, value);
        String warning = d.getCustomerId() == null ? null
                : ContractRules.warning(threshold, value, original == null ? null : original.contractNumber(), original == null ? null : original.status());
        return new DeploymentContractResponse(d.getId(), original == null ? null : view(original), addenda, value, threshold, expected, warning);
    }

    private static DeploymentContractResponse.Contract view(ContractSummary c) {
        return new DeploymentContractResponse.Contract(c.id(), c.contractNumber(), c.title(), c.status(), c.startDate(), c.endDate());
    }

    private EarthDeployment deployment(TenantId tenantId, UUID id) {
        return deployments.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Deployment", id.toString()));
    }

    private EarthAsset asset(TenantId tenantId, EarthDeployment d) {
        return assets.findActiveById(tenantId, d.getAssetId()).orElseThrow(() -> new ResourceNotFoundException("Asset", d.getAssetId().toString()));
    }

    private static String describe(EarthAsset a) {
        String n = (a.getFleetNumber() == null || a.getFleetNumber().isBlank() ? "" : a.getFleetNumber() + " ") + a.getName();
        String m = String.join(" ", java.util.stream.Stream.of(a.getMake(), a.getModel()).filter(x -> x != null && !x.isBlank()).toList());
        return m.isEmpty() ? n : n + " (" + m + ")";
    }

    private static void put(Map<String, String> m, String k, String v) { if (v != null && !v.isBlank()) m.put(k, v); }

    private void audit(TenantId tenantId, String action, EarthDeployment d, ContractSummary c, UUID actor) {
        trail.record(tenantId, action, "EARTHMOVING_DEPLOYMENT", d.getId(), Map.of("contractId", String.valueOf(d.getContractId())),
                Map.of("contractId", String.valueOf(c.id()), "contractNumber", String.valueOf(c.contractNumber()), "status", String.valueOf(c.status())));
    }
}
