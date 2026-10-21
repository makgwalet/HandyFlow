package za.co.handyflow.platform.crm.application.internal;

import za.co.handyflow.platform.crm.domain.model.AuditAction;
import za.co.handyflow.platform.crm.domain.model.AuditEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.domain.repository.OpportunityRepository;
import za.co.handyflow.platform.crm.dto.HierarchyResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/** Head office / branch links between companies. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyHierarchyService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final CustomerRepository customerRepository;
    private final OpportunityRepository opportunityRepository;
    private final CrmAuditService audit;

    @Transactional(readOnly = true)
    public HierarchyResponse hierarchy(TenantId tenantId, UUID id) {
        Customer self = require(tenantId, id);
        Map<UUID, UUID> links = links(tenantId);
        List<UUID> above = HierarchyRules.rootFirst(HierarchyRules.ancestors(id, links));
        Set<UUID> below = HierarchyRules.descendants(id, links);
        List<UUID> directKids = links.entrySet().stream().filter(e -> e.getValue().equals(id)).map(Map.Entry::getKey).toList();

        List<UUID> wanted = new ArrayList<>(above);
        wanted.addAll(directKids);
        Map<UUID, Customer> byId = new HashMap<>();
        if (!wanted.isEmpty()) for (Customer c : customerRepository.findActiveByIds(tenantId, wanted)) byId.put(c.getId(), c);

        List<HierarchyResponse.Node> ancestors = above.stream().filter(byId::containsKey).map(a -> node(byId.get(a))).toList();
        List<HierarchyResponse.Node> children = directKids.stream().filter(byId::containsKey).map(k -> node(byId.get(k)))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();

        List<UUID> group = new ArrayList<>(below);
        group.add(id);
        var items = opportunityRepository.findActiveForCustomers(tenantId, group).stream()
                .map(o -> new OpportunityRules.Item(o.getStage(), o.getAmount(), o.getExpectedCloseDate())).toList();
        var s = OpportunityRules.summarise(items, LocalDate.now(ZONE));
        var rollup = new HierarchyResponse.Rollup(group.size(), s.openCount(), s.openValue(), s.weightedValue(), s.wonCount(), s.wonValue());
        return new HierarchyResponse(node(self), ancestors, children, below.size(), rollup);
    }

    /** Every company that is part of a group (has a parent or branches), for the group tree. */
    @Transactional(readOnly = true)
    public List<HierarchyResponse.Node> groups(TenantId tenantId) {
        Map<UUID, UUID> links = links(tenantId);
        java.util.Set<UUID> ids = new java.util.LinkedHashSet<>(links.keySet());
        ids.addAll(links.values());
        if (ids.isEmpty()) return List.of();
        return customerRepository.findActiveByIds(tenantId, ids).stream().map(this::node)
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
    }

    @Transactional
    public HierarchyResponse setParent(TenantId tenantId, UUID id, UUID parentId) {
        Customer c = require(tenantId, id);
        if (parentId != null) require(tenantId, parentId);
        HierarchyRules.checkParent(id, parentId, links(tenantId));
        c.changeParent(parentId);
        customerRepository.save(c);
        log.info("[CRM] Company {} parent set to {} tenant={}", id, parentId, tenantId);
        audit.record(tenantId, AuditEntity.COMPANY_GROUP, id, c.getName(), AuditAction.PARENT_CHANGED, parentId == null ? "Made top-level" : "Moved under " + customerRepository.findActiveById(tenantId, parentId).map(Customer::getName).orElse("another company"));
        return hierarchy(tenantId, id);
    }

    private Customer require(TenantId tenantId, UUID id) {
        return customerRepository.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Customer", id.toString()));
    }

    private Map<UUID, UUID> links(TenantId tenantId) {
        Map<UUID, UUID> out = new HashMap<>();
        for (Object[] row : customerRepository.findParentLinks(tenantId)) out.put((UUID) row[0], (UUID) row[1]);
        return out;
    }

    private HierarchyResponse.Node node(Customer c) {
        return new HierarchyResponse.Node(c.getId(), c.getName(), c.getParentCustomerId(), c.getCustomerType(), c.getStatus());
    }
}
