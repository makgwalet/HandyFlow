package za.co.handyflow.platform.supplychain.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.SupplierComplianceRules.DocView;
import za.co.handyflow.platform.supplychain.application.internal.SupplierComplianceRules.Overall;
import za.co.handyflow.platform.supplychain.application.internal.SupplierComplianceRules.Result;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierDocType;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierDocument;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierDocumentRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierRepository;
import za.co.handyflow.platform.supplychain.dto.SupplierComplianceResponse;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Per-tenant-configurable compliance policy (global config today):
 * <pre>
 * app.scm.supplier.required-documents=TAX_CLEARANCE,COID_GOOD_STANDING   # empty (default) = no policy
 * app.scm.supplier.document-expiry-warning-days=30
 * app.scm.supplier.require-verified-documents=false
 * </pre>
 * With a policy set, a supplier cannot be approved, reinstated or reopened to ACTIVE while
 * non-compliant (see SupplierLifecycleService). Leaving the property empty changes nothing.
 */
@Slf4j
@Service
public class SupplierComplianceService {

    private final ScSupplierDocumentRepository docRepo;
    private final ScSupplierRepository supplierRepo;
    private final Set<SupplierDocType> required;
    private final int warnDays;
    private final boolean requireVerified;

    public SupplierComplianceService(
            ScSupplierDocumentRepository docRepo,
            ScSupplierRepository supplierRepo,
            @Value("${app.scm.supplier.required-documents:}") String requiredCsv,
            @Value("${app.scm.supplier.document-expiry-warning-days:30}") int warnDays,
            @Value("${app.scm.supplier.require-verified-documents:false}") boolean requireVerified) {
        this.docRepo = docRepo;
        this.supplierRepo = supplierRepo;
        this.warnDays = warnDays;
        this.requireVerified = requireVerified;
        this.required = parse(requiredCsv);
    }

    public Set<SupplierDocType> requiredTypes() { return required; }
    public int warnDays() { return warnDays; }

    @Transactional(readOnly = true)
    public Result evaluate(TenantId tenantId, UUID supplierId) {
        List<DocView> docs = docRepo.findActiveForSupplier(tenantId.getValue(), supplierId).stream()
                .map(d -> new DocView(d.getDocType(), d.getExpiryDate(), d.isVerified())).toList();
        return SupplierComplianceRules.evaluate(required, docs, LocalDate.now(), warnDays, requireVerified);
    }

    /** One query for the whole tenant — used by the scorecard list. */
    @Transactional(readOnly = true)
    public Map<UUID, Overall> overallForTenant(TenantId tenantId, Collection<UUID> supplierIds) {
        Map<UUID, List<DocView>> bySupplier = docRepo.findActiveForTenant(tenantId.getValue()).stream()
                .collect(Collectors.groupingBy(ScSupplierDocument::getSupplierId,
                        Collectors.mapping(d -> new DocView(d.getDocType(), d.getExpiryDate(), d.isVerified()), Collectors.toList())));
        Map<UUID, Overall> out = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (UUID id : supplierIds) {
            out.put(id, SupplierComplianceRules.evaluate(required, bySupplier.getOrDefault(id, List.of()), today, warnDays, requireVerified).overall());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public SupplierComplianceResponse describe(TenantId tenantId, UUID supplierId) {
        supplierRepo.findByTenantIdAndId(tenantId.getValue(), supplierId)
                .orElseThrow(() -> new HandyFlowException("Supplier not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        Result r = evaluate(tenantId, supplierId);
        return new SupplierComplianceResponse(supplierId, r.overall().name(),
                r.items().stream().map(i -> new SupplierComplianceResponse.Item(i.type().name(), i.status().name(),
                        i.expiry(), i.daysToExpiry())).toList());
    }

    /** Throws 409 when the tenant has a policy and the supplier fails it. EXPIRING is still allowed. */
    @Transactional(readOnly = true)
    public void requireCompliant(TenantId tenantId, UUID supplierId) {
        Result r = evaluate(tenantId, supplierId);
        if (r.overall() == Overall.NON_COMPLIANT) {
            String why = r.problems().stream().map(i -> i.type() + " " + i.status().name().toLowerCase())
                    .collect(Collectors.joining(", "));
            throw new HandyFlowException("Supplier is not compliant: " + why
                    + ". Upload the missing/renewed documents first.", HttpStatus.CONFLICT, "SUPPLIER_NOT_COMPLIANT");
        }
    }

    private static Set<SupplierDocType> parse(String csv) {
        Set<SupplierDocType> out = EnumSet.noneOf(SupplierDocType.class);
        if (csv == null || csv.isBlank()) return out;
        for (String t : csv.split(",")) {
            String name = t.trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty()) continue;
            try { out.add(SupplierDocType.valueOf(name)); }
            catch (IllegalArgumentException e) { log.warn("[SCM] Ignoring unknown value in app.scm.supplier.required-documents: {}", name); }
        }
        return out;
    }
}
