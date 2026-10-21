package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.application.internal.SegmentRules.Condition;
import za.co.handyflow.platform.crm.application.internal.SegmentRules.Subject;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.Segment;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.domain.repository.SegmentRepository;
import za.co.handyflow.platform.crm.dto.SegmentConditionDto;
import za.co.handyflow.platform.crm.dto.SegmentMembersResponse;
import za.co.handyflow.platform.crm.dto.SegmentRequest;
import za.co.handyflow.platform.crm.dto.SegmentResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Saved segments and who is in them. Membership is worked out from the live customer list each time. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SegmentService {

    static final int MEMBER_LIMIT = 200;

    private final SegmentRepository repository;
    private final CustomerRepository customers;

    @Transactional(readOnly = true)
    public List<SegmentResponse> list(TenantId tenantId) {
        var everyone = customers.findAllActiveForExport(tenantId);
        Instant now = Instant.now();
        return repository.findAllForTenant(tenantId).stream()
                .map(s -> toResponse(s, count(conditionsOf(s), everyone, now))).toList();
    }

    @Transactional
    public SegmentResponse create(TenantId tenantId, UUID userId, SegmentRequest req) {
        var conditions = checked(req);
        if (repository.findAllForTenant(tenantId).size() >= SegmentRules.MAX_SEGMENTS) {
            throw new IllegalArgumentException("An account can have at most " + SegmentRules.MAX_SEGMENTS + " segments.");
        }
        var segment = repository.save(Segment.create(tenantId, req.name(), toStored(conditions), userId));
        log.info("[CRM] Segment created id={} tenant={}", segment.getId(), tenantId);
        return toResponse(segment, count(conditions, customers.findAllActiveForExport(tenantId), Instant.now()));
    }

    @Transactional
    public SegmentResponse update(TenantId tenantId, UUID id, SegmentRequest req) {
        var segment = find(tenantId, id);
        var conditions = checked(req);
        segment.update(req.name(), toStored(conditions));
        repository.save(segment);
        return toResponse(segment, count(conditions, customers.findAllActiveForExport(tenantId), Instant.now()));
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        repository.delete(find(tenantId, id));
    }

    /** How many customers a set of conditions would match, with a sample, without saving anything. */
    @Transactional(readOnly = true)
    public SegmentMembersResponse preview(TenantId tenantId, SegmentRequest req) {
        List<Condition> conditions = SegmentRules.clean(fromDtos(req.conditions()));
        String problem = SegmentRules.problem(conditions);
        if (problem != null) throw new IllegalArgumentException(problem);
        return members(conditions, customers.findAllActiveForExport(tenantId), Instant.now());
    }

    @Transactional(readOnly = true)
    public SegmentMembersResponse members(TenantId tenantId, UUID id) {
        return members(conditionsOf(find(tenantId, id)), customers.findAllActiveForExport(tenantId), Instant.now());
    }

    private Segment find(TenantId tenantId, UUID id) {
        return repository.findByIdAndTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Segment", id.toString()));
    }

    private List<Condition> checked(SegmentRequest req) {
        String name = req.name() == null ? "" : req.name().strip();
        if (name.isEmpty() || name.length() > SegmentRules.MAX_NAME) {
            throw new IllegalArgumentException("A segment name must be 1 to " + SegmentRules.MAX_NAME + " characters.");
        }
        List<Condition> conditions = SegmentRules.clean(fromDtos(req.conditions()));
        String problem = SegmentRules.problem(conditions);
        if (problem != null) throw new IllegalArgumentException(problem);
        return conditions;
    }

    private static List<Condition> fromDtos(List<SegmentConditionDto> dtos) {
        return dtos == null ? List.of() : dtos.stream().map(d -> d == null ? null : new Condition(d.field(), d.op(), d.value())).toList();
    }

    private static List<Map<String, String>> toStored(List<Condition> conditions) {
        return conditions.stream().map(c -> {
            Map<String, String> m = new HashMap<>();
            m.put("field", c.field());
            m.put("op", c.op());
            m.put("value", c.value());
            return m;
        }).toList();
    }

    private static List<Condition> conditionsOf(Segment s) {
        return s.getConditions().stream().map(m -> new Condition(m.get("field"), m.get("op"), m.get("value"))).toList();
    }

    private static Subject subject(Customer c) {
        return new Subject(c.getCustomerType(), c.getStatus(), c.getLeadSource(), c.getPipelineStage(), c.getOwnerId(),
                c.getTags() == null ? java.util.Set.of() : new LinkedHashSet<>(c.getTags()), c.getDealValue(), c.getCreatedAt(), c.getUpdatedAt(),
                c.getEmail() != null && !c.getEmail().isBlank(), c.getPhone() != null && !c.getPhone().isBlank());
    }

    private static int count(List<Condition> conditions, List<Customer> everyone, Instant now) {
        int n = 0;
        for (Customer c : everyone) if (SegmentRules.matches(conditions, subject(c), now)) n++;
        return n;
    }

    private static SegmentMembersResponse members(List<Condition> conditions, List<Customer> everyone, Instant now) {
        var rows = new java.util.ArrayList<SegmentMembersResponse.Member>();
        int total = 0;
        for (Customer c : everyone) {
            if (!SegmentRules.matches(conditions, subject(c), now)) continue;
            total++;
            if (rows.size() < MEMBER_LIMIT) rows.add(new SegmentMembersResponse.Member(c.getId(), c.getName(), c.getEmail(), c.getPhone(),
                    c.getCustomerType() == null ? null : c.getCustomerType().name(), c.getStatus() == null ? null : c.getStatus().name()));
        }
        return new SegmentMembersResponse(total, total > rows.size(), rows);
    }

    private static SegmentResponse toResponse(Segment s, int count) {
        return new SegmentResponse(s.getId(), s.getName(),
                conditionsOf(s).stream().map(c -> new SegmentConditionDto(c.field(), c.op(), c.value())).toList(), count);
    }
}
