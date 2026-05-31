package za.co.handyflow.platform.billing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.billing.domain.model.ModuleCatalogue;
import za.co.handyflow.platform.billing.domain.model.TenantModule;
import za.co.handyflow.platform.billing.domain.repository.ModuleCatalogueRepository;
import za.co.handyflow.platform.billing.domain.repository.TenantModuleRepository;
import za.co.handyflow.platform.billing.dto.*;
import za.co.handyflow.platform.shared.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.billing.dto.CancelPreviewResponse;
import java.time.Instant;
import java.util.Map;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleCatalogueRepository catalogueRepo;
    private final TenantModuleRepository    tenantModuleRepo;
    private final JdbcTemplate jdbc;

    // ── Catalogue ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ModuleCatalogueResponse> getCatalogue() {
        return catalogueRepo.findAllActive().stream()
                .map(this::toCatalogueResponse)
                .toList();
    }

    // ── Tenant modules ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TenantModuleResponse> getTenantModules(TenantId tenantId) {
        return tenantModuleRepo.findActiveByTenant(tenantId.getValue())
                .stream()
                .map(m -> toTenantModuleResponse(m, tenantId))
                .toList();
    }

    @Transactional
    public TenantModuleResponse activateModule(TenantId tenantId,
                                               String moduleKey,
                                               int trialDays) {
        // Validate module exists
        ModuleCatalogue catalogue = catalogueRepo.findByKey(moduleKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown module: " + moduleKey));

        // Check if already active
        var existing = tenantModuleRepo.findByTenantAndKey(
                tenantId.getValue(), moduleKey);

        TenantModule module;
        if (existing.isPresent()) {
            module = existing.get();
            if ("ACTIVE".equals(module.getStatus()) || "TRIAL".equals(module.getStatus())) {
                log.info("Module {} already active for tenant={}", moduleKey, tenantId);
                return toTenantModuleResponse(module, tenantId);
            }
            // Re-activate cancelled/suspended module
            module.activate();
        } else {
            module = trialDays > 0
                    ? TenantModule.createTrial(tenantId.getValue(), moduleKey, trialDays)
                    : TenantModule.createActive(tenantId.getValue(), moduleKey);
        }

        tenantModuleRepo.save(module);
        log.info("Activated module={} tenant={} status={}", moduleKey, tenantId, module.getStatus());
        return toTenantModuleResponse(module, tenantId);
    }

    @Transactional
    public void activateModules(TenantId tenantId, List<String> moduleKeys,
                                int trialDays) {
        for (String key : moduleKeys) {
            try {
                activateModule(tenantId, key, trialDays);
            } catch (Exception e) {
                log.warn("Failed to activate module={} for tenant={}: {}",
                        key, tenantId, e.getMessage());
            }
        }
    }

    @Transactional
    public void cancelModule(TenantId tenantId, String moduleKey) {
        tenantModuleRepo.findByTenantAndKey(tenantId.getValue(), moduleKey)
                .ifPresent(m -> {
                    m.cancel();
                    tenantModuleRepo.save(m);
                    log.info("Cancelled module={} tenant={}", moduleKey, tenantId);
                });
    }

    @Transactional(readOnly = true)
    public boolean hasAccess(TenantId tenantId, String moduleKey) {
        return tenantModuleRepo.hasAccess(tenantId.getValue(), moduleKey);
    }

@Transactional(readOnly = true)
public CancelPreviewResponse getCancelPreview(TenantId tenantId, String moduleKey) {
    // WHY JDBC here? We need to count records across modules without
    // creating cross-module JPA dependencies.
    // Each count query is module-specific and safe to read.
    int recordCount = switch (moduleKey) {
        case "hr"          -> countRows("hr_employees",    tenantId);
        case "security"    -> countRows("security_guards", tenantId);
        case "fleet"       -> countRows("fleet_vehicles",  tenantId);
        case "fuel"        -> countRows("fuel_tanks",       tenantId);
        case "property"    -> countRows("property_properties", tenantId);
        case "clinic"      -> countRows("clinic_patients", tenantId);
        case "bookings"    -> countRows("bookings",        tenantId);
        case "events"      -> countRows("events",          tenantId);
        case "accounting"  -> countRows("acc_accounts",   tenantId);
        case "expenses"    -> countRows("expense_claims",  tenantId);
        case "contracting" -> countRows("contracts",       tenantId);
        case "invoicing"   -> countRows("invoices",        tenantId);
        case "earthmoving" -> countRows("earthmoving_assets", tenantId);
        default -> 0;
    };

    // Calculate access until date for display
    var existing = tenantModuleRepo.findByTenantAndKey(tenantId.getValue(), moduleKey);
    Instant accessUntil = existing.map(m -> {
        // Simulate cancel to get the grace period end date
        var sim = TenantModule.createActive(tenantId.getValue(), moduleKey);
        return sim.calculateEndOfBillingPeriodPublic();
    }).orElse(null);

    String moduleName = catalogueRepo.findByKey(moduleKey)
        .map(ModuleCatalogue::getName).orElse(moduleKey);

    return new CancelPreviewResponse(
        moduleKey, moduleName, recordCount,
        recordCount > 0
            ? "Cancelling will hide " + recordCount + " records. Your data is kept and restored if you re-activate."
            : "No data will be affected.",
        accessUntil
    );
}

private int countRows(String table, TenantId tenantId) {
    try {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?",
            Integer.class, tenantId.getValue());
        return count != null ? count : 0;
    } catch (Exception e) {
        log.warn("Could not count rows in {}: {}", table, e.getMessage());
        return 0;
    }
}

    // ── Mappers ───────────────────────────────────────────────────────────────

    private ModuleCatalogueResponse toCatalogueResponse(ModuleCatalogue m) {
        return new ModuleCatalogueResponse(m.getId(), m.getKey(), m.getName(),
                m.getDescription(), m.getMonthlyPrice(), m.getCurrency(),
                m.getIcon(), m.getCategory(), m.getSortOrder());
    }

    private TenantModuleResponse toTenantModuleResponse(TenantModule m,
                                                        TenantId tenantId) {
        String name = catalogueRepo.findByKey(m.getModuleKey())
                .map(ModuleCatalogue::getName).orElse(m.getModuleKey());
        String desc = catalogueRepo.findByKey(m.getModuleKey())
                .map(ModuleCatalogue::getDescription).orElse(null);
        var price = catalogueRepo.findByKey(m.getModuleKey())
                .map(ModuleCatalogue::getMonthlyPrice).orElse(null);
        return new TenantModuleResponse(m.getId(), m.getModuleKey(), name, desc,
                price, m.getStatus(), m.getTrialEndsAt(), m.getActivatedAt(),
                m.isAccessible());
    }
}

