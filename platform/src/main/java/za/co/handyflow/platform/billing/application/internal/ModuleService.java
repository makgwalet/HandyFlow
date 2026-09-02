package za.co.handyflow.platform.billing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.admin.DiscountFacade;
import za.co.handyflow.platform.billing.domain.model.ModuleCatalogue;
import za.co.handyflow.platform.billing.domain.model.TenantModule;
import za.co.handyflow.platform.billing.domain.repository.ModuleCatalogueRepository;
import za.co.handyflow.platform.billing.domain.repository.TenantModuleRepository;
import za.co.handyflow.platform.billing.dto.*;
import za.co.handyflow.platform.shared.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.billing.dto.CancelPreviewResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import java.util.List;
import java.util.UUID;

/**
 * FIX: Discount Engine wiring session, Piece A. activateModule() now
 * resolves and locks in a discount at activation time via
 * admin.DiscountFacade — your confirmed decision (Option A): stored on
 * TenantModule itself (discountPct/discountSource), not recomputed on
 * later invoice cycles, and the volume-tier this resolves to is locked
 * in too, not re-evaluated as the tenant's module count changes in
 * future months. See DiscountFacade's own class Javadoc for why billing
 * reaches admin through a facade rather than calling AdminDiscountService
 * directly (billing's package-info didn't have admin in its allowed
 * dependencies at all before this session).
 * <p>
 * Does NOT touch resolveDiscount()'s own resolution logic — Partnership
 * > Volume > Code priority, non-stacking, all exactly as before. This
 * is wiring, not a redesign, per this session's own explicit instruction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModuleService {

    private final ModuleCatalogueRepository catalogueRepo;
    private final TenantModuleRepository    tenantModuleRepo;
    private final JdbcTemplate jdbc;
    private final DiscountFacade discountFacade;

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
                                               int trialDays,
                                               String discountCode,
                                               UUID activatedBy) {
        // Validate module exists
        ModuleCatalogue catalogue = catalogueRepo.findByKey(moduleKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown module: " + moduleKey));

        // Check if already active
        var existing = tenantModuleRepo.findByTenantAndKey(
                tenantId.getValue(), moduleKey);

        // FIX: trialDays was previously used as-is — a fresh, independent
        // clock starting from right now, regardless of where the
        // account's OWN trial already was. Confirmed via real data this
        // produces exactly the staggered-expiry problem: a batch of
        // modules all activated together share one trial end date, while
        // anything added later runs its own, completely separate clock —
        // multiple simultaneous, uncoordinated trial windows on one
        // tenant, several of which had already silently expired while
        // the account itself was suspended and unable to act on any of
        // them.
        //
        // Every module trial now converges on the SAME decision moment
        // the account itself converges on — capped at
        // subscriptions.pilot_ends_at, never past it. A module added on
        // day 58 of a 60-day trial genuinely only gets 2 days — shown
        // honestly, not padded with an artificial minimum, since a
        // minimum would just reintroduce the same staggering this exists
        // to eliminate.
        int cappedTrialDays = capTrialDaysToAccountTrial(tenantId, trialDays);

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
            module = cappedTrialDays > 0
                    ? TenantModule.createTrial(tenantId.getValue(), moduleKey, cappedTrialDays)
                    : TenantModule.createActive(tenantId.getValue(), moduleKey);
        }

        // FIX: Discount Engine wiring, Piece A. Resolved once here,
        // covering both branches above (fresh creation AND
        // re-activation) — a returning tenant with a valid partnership
        // discount or a fresh discount code gets it recorded exactly
        // the same as a brand-new activation. Zero-pct/"NONE" results
        // (the normal case — most activations have no applicable
        // discount) leave discountPct/discountSource null, matching
        // every pre-existing TenantModule row's own correct default
        // state.
        DiscountFacade.DiscountOutcome discount = discountFacade.resolveAndRecordDiscount(
                tenantId.getValue(), moduleKey, discountCode, catalogue.getMonthlyPrice(), activatedBy);
        if (discount.pct() != null && discount.pct().compareTo(BigDecimal.ZERO) > 0) {
            module.applyDiscount(discount.pct(), discount.source());
        }

        tenantModuleRepo.save(module);
        log.info("Activated module={} tenant={} status={} discount={}",
                moduleKey, tenantId, module.getStatus(),
                discount.pct() != null && discount.pct().compareTo(BigDecimal.ZERO) > 0
                        ? discount.source() : "none");
        return toTenantModuleResponse(module, tenantId);
    }

    // Returns the requested trial length, or however many whole days
    // remain until the account's own trial ends, whichever is smaller.
    // Falls back to the requested length uncapped if the account has no
    // pilot window at all (e.g. already on a paid plan) or the lookup
    // fails for any reason — a missing cap should never block module
    // activation outright.
    private int capTrialDaysToAccountTrial(TenantId tenantId, int requestedTrialDays) {
        try {
            java.sql.Timestamp pilotEndsAt = jdbc.queryForObject(
                    "SELECT pilot_ends_at FROM subscriptions WHERE tenant_id = ?",
                    java.sql.Timestamp.class, tenantId.getValue());
            if (pilotEndsAt == null) return requestedTrialDays;

            long daysUntilAccountTrialEnds = java.time.temporal.ChronoUnit.DAYS.between(
                    Instant.now(), pilotEndsAt.toInstant());
            return (int) Math.max(0, Math.min(requestedTrialDays, daysUntilAccountTrialEnds));
        } catch (Exception e) {
            log.warn("Could not resolve account trial end for tenant={}, using requested trial days uncapped: {}",
                    tenantId, e.getMessage());
            return requestedTrialDays;
        }
    }

    @Transactional
    public void activateModules(TenantId tenantId, List<String> moduleKeys,
                                int trialDays, UUID activatedBy) {
        for (String key : moduleKeys) {
            try {
                // FIX: Discount Engine wiring — batch activation (e.g.
                // onboarding, activating several modules together) has
                // no per-module discount code concept, matching this
                // method's own pre-existing shape (no discountCode
                // parameter here either) — discountCode is null for
                // every module in the batch. Partnership/volume
                // discounts still resolve and apply normally; only a
                // one-time CODE specifically requires the single-module
                // activateModule() call to supply one.
                activateModule(tenantId, key, trialDays, null, activatedBy);
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

    // NEW: called when an account's own trial/subscription ends (see
    // SubscriptionService.suspendExpiredPilots()/suspendGraceExpired()).
    // Confirmed via real data this was genuinely needed: without it, a
    // module still sitting in TRIAL status (not yet expired, not yet
    // cancelled) at the exact moment the account gets suspended stays in
    // that ambiguous state indefinitely — meaning if the account is ever
    // reactivated later, that module could silently reappear as
    // accessible again with no explicit re-activation decision, purely
    // because its own trial happened not to have expired yet. Matches
    // the explicit direction this was built to: when the account trial
    // ends, every module trial ends with it, together — not staggered,
    // not left dangling, no silent survivors.
    //
    // Reuses the same findByTenantAndKey + cancel() pattern
    // cancelModule() already uses above, rather than a raw bulk SQL
    // update, so any side effects the entity's own cancel() method has
    // (timestamps, domain events, whatever else it does) aren't bypassed
    // — only the read-only lookup of which keys need cancelling goes
    // through jdbc directly.
    @Transactional
    public void cancelAllTrialModules(TenantId tenantId) {
        List<String> trialModuleKeys = jdbc.queryForList(
                "SELECT module_key FROM tenant_modules WHERE tenant_id = ? AND status = 'TRIAL'",
                String.class, tenantId.getValue());

        for (String moduleKey : trialModuleKeys) {
            try {
                tenantModuleRepo.findByTenantAndKey(tenantId.getValue(), moduleKey)
                        .ifPresent(module -> {
                            module.cancel();
                            tenantModuleRepo.save(module);
                        });
            } catch (Exception e) {
                log.warn("Failed to cancel trial module={} for tenant={}: {}", moduleKey, tenantId, e.getMessage());
            }
        }

        log.info("Cancelled {} trial module(s) for tenant={} — account trial/subscription ended",
                trialModuleKeys.size(), tenantId);
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