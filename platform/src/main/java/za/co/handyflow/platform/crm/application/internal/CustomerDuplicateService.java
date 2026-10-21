package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.CustomerDuplicateDismissal;
import za.co.handyflow.platform.crm.domain.repository.CustomerDuplicateDismissalRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.dto.DuplicatesResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Finds likely duplicate customers and remembers the pairs a person has dismissed. Nothing is merged or deleted here. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerDuplicateService {

    /** Most customers compared in one go. A tenant with more is told the scan was cut. */
    public static final int SCAN_LIMIT = 5000;

    private final CustomerRepository customerRepository;
    private final CustomerDuplicateDismissalRepository dismissalRepository;

    @Transactional(readOnly = true)
    public DuplicatesResponse find(TenantId tenantId) {
        List<Customer> customers = new ArrayList<>(customerRepository.findAllActive(tenantId, PageRequest.of(0, SCAN_LIMIT + 1)).getContent());
        boolean truncated = customers.size() > SCAN_LIMIT;
        if (truncated) customers = customers.subList(0, SCAN_LIMIT);
        List<DuplicateMatcher.Candidate> candidates = customers.stream()
                .map(c -> new DuplicateMatcher.Candidate(c.getId(), c.getName(), c.getPhone(), c.getTaxNumber()))
                .toList();
        Set<String> dismissed = new HashSet<>(dismissalRepository.findPairKeys(tenantId));
        Map<UUID, Customer> byId = customers.stream().collect(Collectors.toMap(Customer::getId, Function.identity()));
        List<DuplicatesResponse.Group> groups = DuplicateMatcher.find(candidates, dismissed,
                (a, b) -> CustomerNameSimilarityChecker.jaroWinkler(a, b) >= CustomerNameSimilarityChecker.THRESHOLD).stream()
                .map(g -> new DuplicatesResponse.Group(
                        g.ids().stream().map(byId::get)
                                .map(c -> new DuplicatesResponse.Member(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getTaxNumber(), c.getCustomerType(), c.getCreatedAt()))
                                .toList(),
                        g.reasons()))
                .toList();
        return new DuplicatesResponse(groups, customers.size(), truncated);
    }

    /** Marks every pair among the given customers as not duplicates. Asking twice is harmless. */
    @Transactional
    public int dismiss(TenantId tenantId, List<UUID> customerIds) {
        Set<UUID> ids = new LinkedHashSet<>(customerIds);
        if (ids.size() < 2) throw new IllegalArgumentException("Choose at least two different customers");
        for (UUID id : ids) {
            if (!customerRepository.existsActiveById(tenantId, id)) throw new ResourceNotFoundException("Customer", id.toString());
        }
        Set<String> known = new HashSet<>(dismissalRepository.findPairKeys(tenantId));
        List<UUID> list = new ArrayList<>(ids);
        UUID by = currentUserId();
        int added = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                String key = DuplicateMatcher.pairKey(list.get(i), list.get(j));
                if (known.add(key)) {
                    dismissalRepository.save(CustomerDuplicateDismissal.of(tenantId, key, list.get(i), list.get(j), by));
                    added++;
                }
            }
        }
        log.info("[CRM] Duplicate pairs dismissed count={} tenant={}", added, tenantId);
        return added;
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) return null;
        try {
            return UUID.fromString(auth.getName());
        } catch (Exception e) {
            return null;
        }
    }
}
