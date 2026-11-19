package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.application.internal.AuthorityRules.Context;
import za.co.handyflow.platform.contracting.application.internal.AuthorityRules.Decision;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Delegated authority for contracts. Configured per organisation; enforced only when an administrator switches enforcement on, so a
 * tenant that has not set anything up behaves as before. When enforced, an action that has an authority meaning needs a grant that covers
 * it, and nobody can exceed what they hold. Every change is written to an append-only audit trail.
 */
@Service
@RequiredArgsConstructor
public class ContractAuthorityService {
    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ContractAuthorityGrantRepository grantRepo;
    private final ContractAuthorityEventRepository eventRepo;
    private final ContractAuthoritySettingsRepository settingsRepo;
    private final ContractRepository contractRepo;

    // ── settings ──

    @Transactional(readOnly = true)
    public boolean enforced(TenantId tenantId) {
        return settingsRepo.findById(tenantId.getValue()).map(ContractAuthoritySettings::isEnforced).orElse(false);
    }

    @Transactional
    public boolean setEnforced(TenantId tenantId, boolean enforced, UUID by) {
        ContractAuthoritySettings s = settingsRepo.findById(tenantId.getValue()).orElseGet(() -> ContractAuthoritySettings.create(tenantId.getValue()));
        boolean was = s.isEnforced();
        s.set(enforced, by);
        settingsRepo.save(s);
        if (was != enforced) event(tenantId, null, enforced ? "ENFORCEMENT_ON" : "ENFORCEMENT_OFF", by, null);
        return enforced;
    }

    // ── grants ──

    @Transactional(readOnly = true)
    public List<ContractAuthorityGrant> grants(TenantId tenantId) { return grantRepo.findByTenant(tenantId.getValue()); }

    @Transactional(readOnly = true)
    public List<ContractAuthorityEvent> events(TenantId tenantId) { return eventRepo.recent(tenantId.getValue(), PageRequest.of(0, 200)); }

    public record GrantRequest(UUID userId, String role, String businessUnit, Set<String> contractTypes, Set<AuthorityAction> actions,
                               boolean unlimited, BigDecimal maxAmount, LocalDate effectiveFrom, LocalDate expiresOn, String conditions) {}

    @Transactional
    public ContractAuthorityGrant grant(TenantId tenantId, GrantRequest q, UUID by) {
        if ((q.userId() == null) == (q.role() == null || q.role().isBlank()))
            throw new IllegalArgumentException("Give authority to either one person or one role");
        if (q.actions() == null || q.actions().isEmpty()) throw new IllegalArgumentException("Choose at least one action");
        if (!q.unlimited() && (q.maxAmount() == null || q.maxAmount().signum() < 0))
            throw new IllegalArgumentException("Set the most this authority covers, or choose no financial limit");
        LocalDate from = q.effectiveFrom() != null ? q.effectiveFrom() : today();
        if (q.expiresOn() != null && q.expiresOn().isBefore(from)) throw new IllegalArgumentException("The end date cannot be before the start date");
        ContractAuthorityGrant g = grantRepo.save(ContractAuthorityGrant.create(tenantId.getValue(), q.userId(),
                q.userId() == null ? q.role().trim() : null, blankToNull(q.businessUnit()),
                q.contractTypes() == null ? Set.of() : q.contractTypes(), q.actions(), q.unlimited(), q.maxAmount(), from, q.expiresOn(),
                blankToNull(q.conditions()), by));
        event(tenantId, g.getId(), "GRANTED", by, describe(g));
        return g;
    }

    public record DelegationRequest(UUID toUserId, Set<AuthorityAction> actions, Set<String> contractTypes, boolean unlimited, BigDecimal maxAmount,
                                    LocalDate effectiveFrom, LocalDate expiresOn, String conditions) {}

    /** The holder of an authority passes some of it, for a stated period, to someone else. It can never be more than they hold. */
    @Transactional
    public ContractAuthorityGrant delegate(TenantId tenantId, UUID sourceId, DelegationRequest q, UUID by, Collection<String> roles) {
        ContractAuthorityGrant source = grantRepo.findOne(tenantId.getValue(), sourceId).orElseThrow(() -> new ResourceNotFoundException("Authority", sourceId.toString()));
        Map<UUID, ContractAuthorityGrant> all = byId(grants(tenantId));
        LocalDate today = today();
        LocalDate from = q.effectiveFrom() != null ? q.effectiveFrom() : today;
        Set<String> types = q.contractTypes() == null ? Set.of() : q.contractTypes();
        String problem = AuthorityRules.checkDelegation(source, all, by, roles, q.toUserId(), q.actions(), types, q.unlimited(), q.maxAmount(), from, q.expiresOn(), today);
        if (problem != null) throw new IllegalArgumentException(problem);
        ContractAuthorityGrant g = grantRepo.save(ContractAuthorityGrant.delegate(source, q.toUserId(), types.isEmpty() ? source.typeSet() : types,
                q.actions(), q.unlimited(), q.maxAmount(), from, q.expiresOn(), blankToNull(q.conditions()), by));
        event(tenantId, g.getId(), "DELEGATED", by, "From " + source.getId() + ". " + describe(g));
        return g;
    }

    @Transactional
    public ContractAuthorityGrant revoke(TenantId tenantId, UUID id, String reason, UUID by) {
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Say why this authority is being revoked");
        ContractAuthorityGrant g = grantRepo.findOne(tenantId.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("Authority", id.toString()));
        g.revoke(by, reason.trim());
        grantRepo.save(g);
        event(tenantId, id, "REVOKED", by, reason.trim());
        return g;
    }

    // ── checks ──

    @Transactional(readOnly = true)
    public Decision decide(TenantId tenantId, UUID userId, Collection<String> roles, AuthorityAction action, Context ctx) {
        return AuthorityRules.evaluate(grants(tenantId), userId, roles, action, ctx, today());
    }

    /**
     * Throws when enforcement is on and this person's authority does not cover the action on this contract. With enforcement off it
     * does nothing. Authority is about the organisation's mandate; the person's role permission is checked separately.
     */
    @Transactional(readOnly = true)
    public void assertAuthorised(TenantId tenantId, UUID userId, Collection<String> roles, AuthorityAction action, Context ctx) {
        if (!enforced(tenantId)) return;
        Decision d = decide(tenantId, userId, roles, action, ctx);
        if (!d.allowed()) throw new AccessDeniedException("Not authorised to " + action.name().toLowerCase() + " this contract. " + d.reason());
    }

    /** What this person may do on one contract, for the screen that decides whether to show a button. */
    @Transactional(readOnly = true)
    public Decision decideFor(TenantId tenantId, UUID userId, Collection<String> roles, AuthorityAction action, UUID contractId) {
        Contract c = contractRepo.findActiveById(tenantId, contractId).orElseThrow(() -> new ResourceNotFoundException("Contract", contractId.toString()));
        return decide(tenantId, userId, roles, action, contextOf(c));
    }

    /** The authority the current person holds today, for the screen that shows "what can I sign?". */
    @Transactional(readOnly = true)
    public List<ContractAuthorityGrant> heldBy(TenantId tenantId, UUID userId, Collection<String> roles) {
        List<ContractAuthorityGrant> all = grants(tenantId);
        Map<UUID, ContractAuthorityGrant> byId = byId(all);
        LocalDate today = today();
        return all.stream().filter(g -> AuthorityRules.appliesTo(g, userId, roles) && AuthorityRules.inForce(g, byId, today)).toList();
    }

    /** Same check for whoever is making the current request. Does nothing when enforcement is off. */
    @Transactional(readOnly = true)
    public void assertCurrentUser(TenantId tenantId, AuthorityAction action, Contract c) {
        if (!enforced(tenantId)) return;
        UUID user;
        try { user = za.co.handyflow.platform.shared.TenantContext.getCurrentUserId(); } catch (RuntimeException e) { user = null; }
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Set<String> roles = new HashSet<>();
        if (auth != null) auth.getAuthorities().forEach(a -> roles.add(a.getAuthority()));
        assertAuthorised(tenantId, user, roles, action, contextOf(c));
    }

    public static Context contextOf(Contract c) { return new Context(c.getContractType(), c.getValueAmount(), c.getCurrency()); }

    // ── internals ──

    private static Map<UUID, ContractAuthorityGrant> byId(Collection<ContractAuthorityGrant> list) {
        Map<UUID, ContractAuthorityGrant> m = new HashMap<>();
        for (ContractAuthorityGrant g : list) m.put(g.getId(), g);
        return m;
    }

    private void event(TenantId tenantId, UUID grantId, String type, UUID by, String detail) {
        eventRepo.save(ContractAuthorityEvent.of(tenantId.getValue(), grantId, type, by, detail));
    }

    private static String describe(ContractAuthorityGrant g) {
        return (g.getGranteeUserId() != null ? "User " + g.getGranteeUserId() : "Role " + g.getGranteeRole()) + ": " + g.getActions()
                + (g.getContractTypes().isBlank() ? ", all contract types" : ", types " + g.getContractTypes())
                + (g.isUnlimitedAmount() ? ", no financial limit" : ", up to R" + g.getMaxAmount())
                + ", from " + g.getEffectiveFrom() + (g.getExpiresOn() != null ? " to " + g.getExpiresOn() : ", no end date");
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static LocalDate today() { return LocalDate.now(SAST); }
}
