package za.co.handyflow.platform.crm.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.application.internal.MergeRules.Snap;
import za.co.handyflow.platform.crm.domain.model.AuditAction;
import za.co.handyflow.platform.crm.domain.model.AuditEntity;
import za.co.handyflow.platform.crm.domain.model.CustomField;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.repository.CustomFieldRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.dto.MergePreviewResponse;
import za.co.handyflow.platform.crm.dto.MergeRequest;
import za.co.handyflow.platform.crm.dto.MergeResultResponse;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

/**
 * Folds one customer record into another. The kept record gets the other's contacts, tasks, messages, opportunities, campaign places, timeline,
 * branches and tags, and any blank field is filled from it; the other record is then removed (soft-deleted, like any deletion).
 * It is refused when the removed record is used elsewhere in the system (invoices, bookings and so on), because those are not moved:
 * keep that record instead. All of it happens in one transaction, so a failure leaves both records untouched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerMergeService {
    /** Tables that belong to the CRM and are moved or handled by the merge itself. Every other table with a customer_id column blocks the merge. */
    private static final Set<String> CRM_TABLES = Set.of("contacts", "customer_followups", "customer_communications", "customer_consent", "customer_activities",
            "customer_tags", "customer_duplicate_dismissals");

    private final CustomerRepository customers;
    private final CustomFieldRepository customFields;
    private final CrmAuditService audit;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public MergePreviewResponse preview(TenantId tenantId, UUID keepId, UUID mergeId) {
        var pair = load(tenantId, keepId, mergeId);
        Snap keep = snap(pair.keep), other = snap(pair.other);
        var conflicts = MergeRules.conflicts(keep, other, labels(tenantId)).stream()
                .map(c -> new MergePreviewResponse.Conflict(c.field(), c.label(), c.keep(), c.other())).toList();
        List<String> blockers = new ArrayList<>();
        try { MergeRules.check(keepId, mergeId, pair.keep.getCustomerType(), pair.other.getCustomerType()); } catch (IllegalArgumentException e) { blockers.add(e.getMessage()); }
        try { MergeRules.checkLinks(pair.other.getName(), linksOf(mergeId)); } catch (IllegalStateException e) { blockers.add(e.getMessage()); }
        try { MergeRules.checkGroups(MergeRules.linksAfter(parentLinks(tenantId), keepId, mergeId)); } catch (IllegalArgumentException e) { blockers.add(e.getMessage()); }
        var willFill = MergeRules.filled(keep, MergeRules.merge(keep, other, Set.of(), pair.other.getName()));
        return new MergePreviewResponse(side(pair.keep), side(pair.other), conflicts, moves(tenantId, mergeId), willFill, blockers, blockers.isEmpty());
    }

    @Transactional
    public MergeResultResponse merge(TenantId tenantId, MergeRequest req) {
        var pair = load(tenantId, req.keepId(), req.mergeId());
        Customer keepC = pair.keep, otherC = pair.other;
        try {
            MergeRules.check(req.keepId(), req.mergeId(), keepC.getCustomerType(), otherC.getCustomerType());
            MergeRules.checkLinks(otherC.getName(), linksOf(req.mergeId()));
        } catch (IllegalStateException e) {
            throw new ConflictException("Cannot merge: " + e.getMessage() + ".");
        }
        var links = parentLinks(tenantId);
        MergeRules.checkGroups(MergeRules.linksAfter(links, req.keepId(), req.mergeId()));
        Snap keep = snap(keepC), other = snap(otherC);
        Set<String> take = MergeRules.checkTake(req.take(), keep, other);
        Snap merged = MergeRules.merge(keep, other, take, otherC.getName());
        List<String> filled = MergeRules.filled(keep, merged);
        var moved = moves(tenantId, req.mergeId());
        UUID by = TenantContext.getCurrentUserId();
        UUID parentAfter = MergeRules.linksAfter(links, req.keepId(), req.mergeId()).get(req.keepId());

        em.flush();
        String consentNote = moveRecords(tenantId, req.keepId(), req.mergeId());

        // The removed record goes first, so the kept one can take over its email without two live records sharing it.
        otherC.softDelete(by);
        customers.saveAndFlush(otherC);

        keepC.update(merged.name(), merged.email(), merged.phone(), merged.address(), merged.taxNumber(), merged.notes(), by);
        keepC.assignOwner(merged.ownerId(), by);
        keepC.changeSource(merged.source(), by);
        keepC.updateDeal(merged.dealValue(), merged.closeDate(), by);
        keepC.changeCustomValues(merged.custom(), by);
        MergeRules.mergeTags(keepC.getTags(), otherC.getTags()).forEach(t -> keepC.addTag(t, by));
        keepC.changeParent(parentAfter);
        keepC.recordMerge(otherC.getId(), otherC.getName(), filled, by);
        customers.save(keepC);

        log.info("[CRM] Customers merged keep={} merged={} tenant={} by={}", req.keepId(), req.mergeId(), tenantId, by);
        audit.record(tenantId, AuditEntity.CUSTOMER, req.keepId(), keepC.getName(), AuditAction.MERGED, "Merged in " + otherC.getName());
        return new MergeResultResponse(keepC.getId(), keepC.getName(), filled, moved, consentNote);
    }

    // ── Moving the records ──────────────────────────────────────────────────
    /** Points everything the CRM holds about the removed record at the kept one. Returns a note when consent was left behind. */
    private String moveRecords(TenantId tenantId, UUID keep, UUID other) {
        UUID t = tenantId.getValue();
        // Contacts: a contact with the same email as one already on the kept record would break the one-per-email rule, so it is dropped; one primary only.
        run("UPDATE contacts o SET deleted_at = now() WHERE o.customer_id = :other AND o.tenant_id = :t AND o.deleted_at IS NULL AND o.email IS NOT NULL "
                + "AND EXISTS (SELECT 1 FROM contacts k WHERE k.customer_id = :keep AND k.deleted_at IS NULL AND k.email = o.email)", keep, other, t);
        run("UPDATE contacts SET is_primary = false WHERE customer_id = :other AND tenant_id = :t AND is_primary "
                + "AND EXISTS (SELECT 1 FROM contacts k WHERE k.customer_id = :keep AND k.deleted_at IS NULL AND k.is_primary)", keep, other, t);
        run("UPDATE contacts SET customer_id = :keep WHERE customer_id = :other AND tenant_id = :t", keep, other, t);
        for (String table : List.of("customer_followups", "customer_communications", "customer_activities", "crm_opportunities", "crm_workflow_runs", "crm_journey_runs")) {
            run("UPDATE " + table + " SET customer_id = :keep WHERE customer_id = :other AND tenant_id = :t", keep, other, t);
        }
        // A person can be in a campaign once: where the kept record is already in it, the other's place is dropped.
        run("DELETE FROM crm_campaign_members WHERE customer_id = :other AND tenant_id = :t AND campaign_id IN "
                + "(SELECT campaign_id FROM crm_campaign_members WHERE customer_id = :keep AND tenant_id = :t)", keep, other, t);
        run("UPDATE crm_campaign_members SET customer_id = :keep WHERE customer_id = :other AND tenant_id = :t", keep, other, t);
        // Consent: withdrawn history always moves; a live consent moves only if the kept record has none, so the merge never grants consent the kept record did not have.
        run("UPDATE customer_consent SET customer_id = :keep WHERE customer_id = :other AND tenant_id = :t AND withdrawn_at IS NOT NULL", keep, other, t);
        int liveMoved = run("UPDATE customer_consent SET customer_id = :keep WHERE customer_id = :other AND tenant_id = :t AND withdrawn_at IS NULL "
                + "AND NOT EXISTS (SELECT 1 FROM customer_consent k WHERE k.customer_id = :keep AND k.tenant_id = :t AND k.withdrawn_at IS NULL)", keep, other, t);
        long liveLeft = count("SELECT count(*) FROM customer_consent WHERE customer_id = :other AND tenant_id = :t AND withdrawn_at IS NULL", other, t);
        run("UPDATE customers SET parent_customer_id = :keep WHERE parent_customer_id = :other AND id <> :keep AND tenant_id = :t", keep, other, t);
        run("DELETE FROM customer_duplicate_dismissals WHERE tenant_id = :t AND (customer_a = :other OR customer_b = :other)", keep, other, t);
        if (liveLeft > 0) return "The kept record already had its own consent, so the removed record's consent was not moved. Check the kept record's consent is right.";
        return liveMoved > 0 ? "The removed record's consent was moved onto the kept record." : null;
    }

    private int run(String sql, UUID keep, UUID other, UUID tenant) {
        var q = em.createNativeQuery(sql);
        if (sql.contains(":keep")) q.setParameter("keep", keep);
        q.setParameter("other", other);
        q.setParameter("t", tenant);
        return q.executeUpdate();
    }

    private long count(String sql, UUID id, UUID tenant) {
        return ((Number) em.createNativeQuery(sql).setParameter("other", id).setParameter("t", tenant).getSingleResult()).longValue();
    }

    // ── Reading ─────────────────────────────────────────────────────────────
    private MergePreviewResponse.Moves moves(TenantId tenantId, UUID other) {
        UUID t = tenantId.getValue();
        return new MergePreviewResponse.Moves(
                (int) count("SELECT count(*) FROM contacts WHERE customer_id = :other AND tenant_id = :t AND deleted_at IS NULL", other, t),
                (int) count("SELECT count(*) FROM customer_followups WHERE customer_id = :other AND tenant_id = :t", other, t),
                (int) count("SELECT count(*) FROM customer_communications WHERE customer_id = :other AND tenant_id = :t", other, t),
                (int) count("SELECT count(*) FROM crm_opportunities WHERE customer_id = :other AND tenant_id = :t AND deleted_at IS NULL", other, t),
                (int) count("SELECT count(*) FROM crm_campaign_members WHERE customer_id = :other AND tenant_id = :t", other, t),
                (int) count("SELECT count(*) FROM customers WHERE parent_customer_id = :other AND tenant_id = :t AND deleted_at IS NULL", other, t),
                (int) count("SELECT count(*) FROM customer_activities WHERE customer_id = :other AND tenant_id = :t", other, t));
    }

    /** Rows in tables outside the CRM that point at this customer, by table. Only tables that have any are listed. */
    private Map<String, Long> linksOf(UUID customerId) {
        @SuppressWarnings("unchecked")
        List<String> tables = em.createNativeQuery("SELECT c.table_name FROM information_schema.columns c "
                + "JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name "
                + "WHERE c.column_name = 'customer_id' AND c.table_schema = current_schema() AND t.table_type = 'BASE TABLE'").getResultList();
        Map<String, Long> out = new TreeMap<>();
        for (String table : tables) {
            if (CRM_TABLES.contains(table) || table.startsWith("crm_")) continue;
            long n = ((Number) em.createNativeQuery("SELECT count(*) FROM \"" + table.replace("\"", "") + "\" WHERE customer_id::text = :id")
                    .setParameter("id", customerId.toString()).getSingleResult()).longValue();
            if (n > 0) out.put(table, n);
        }
        return out;
    }

    private Map<UUID, UUID> parentLinks(TenantId tenantId) {
        Map<UUID, UUID> out = new HashMap<>();
        for (Object[] row : customers.findParentLinks(tenantId)) out.put((UUID) row[0], (UUID) row[1]);
        return out;
    }

    private Map<String, String> labels(TenantId tenantId) {
        Map<String, String> out = new LinkedHashMap<>();
        for (CustomField f : customFields.findAllForTenant(tenantId)) out.put(f.getKey(), f.getLabel());
        return out;
    }

    private record Pair(Customer keep, Customer other) {}

    private Pair load(TenantId tenantId, UUID keepId, UUID mergeId) {
        if (keepId == null || mergeId == null || keepId.equals(mergeId)) throw new IllegalArgumentException("Choose two different customers");
        var keep = customers.findActiveById(tenantId, keepId).orElseThrow(() -> new ResourceNotFoundException("Customer", keepId.toString()));
        var other = customers.findActiveById(tenantId, mergeId).orElseThrow(() -> new ResourceNotFoundException("Customer", mergeId.toString()));
        return new Pair(keep, other);
    }

    private static Snap snap(Customer c) {
        return new Snap(c.getName(), c.getEmail(), c.getPhone(), c.getTaxNumber(), c.getNotes(), c.getAddress(), c.getOwnerId(), c.getLeadSource(),
                c.getDealValue(), c.getExpectedCloseDate(), c.getCustomValues() == null ? Map.of() : c.getCustomValues());
    }

    private static MergePreviewResponse.Side side(Customer c) {
        return new MergePreviewResponse.Side(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getCustomerType(), c.getCreatedAt());
    }
}
