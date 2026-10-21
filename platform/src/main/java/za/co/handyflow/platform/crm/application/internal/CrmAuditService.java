package za.co.handyflow.platform.crm.application.internal;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.AuditAction;
import za.co.handyflow.platform.crm.domain.model.AuditEntity;
import za.co.handyflow.platform.crm.domain.model.AuditEvent;
import za.co.handyflow.platform.crm.domain.repository.AuditEventRepository;
import za.co.handyflow.platform.crm.dto.AuditEventResponse;
import za.co.handyflow.platform.shared.TenantId;

/**
 * The CRM audit log. record() joins the caller's transaction, so a change that is rolled back leaves no audit line, and it never throws:
 * a failed audit line must not undo the change it describes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrmAuditService {
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final AuditEventRepository repository;

    public void record(TenantId tenantId, AuditEntity type, UUID entityId, String entityName, AuditAction action, String detail) {
        try {
            repository.save(AuditEvent.of(tenantId, type, entityId, AuditRules.clean(entityName, AuditRules.NAME_MAX), action, AuditRules.clean(detail, AuditRules.DETAIL_MAX), currentUserId()));
        } catch (Exception e) {
            log.warn("[CRM] Audit line not written ({} {} {}) tenant={}: {}", action, type, entityId, tenantId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> search(TenantId tenantId, AuditEntity type, UUID actorId, LocalDate from, LocalDate to, Integer page, Integer size) {
        AuditRules.checkRange(from, to);
        Specification<AuditEvent> spec = (root, q, cb) -> {
            var ps = new ArrayList<Predicate>();
            ps.add(cb.equal(root.get("tenantId"), tenantId));
            if (type != null) ps.add(cb.equal(root.get("entityType"), type));
            if (actorId != null) ps.add(cb.equal(root.get("actorId"), actorId));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay(ZONE).toInstant()));
            if (to != null) ps.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay(ZONE).toInstant()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        var pageable = PageRequest.of(AuditRules.pageNumber(page), AuditRules.pageSize(size), Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        return repository.findAll(spec, pageable).map(e -> new AuditEventResponse(e.getId(), e.getEntityType(), e.getEntityId(), e.getEntityName(), e.getAction(), e.getDetail(), e.getActorId(), e.getCreatedAt()));
    }

    private static UUID currentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) return null;
            return UUID.fromString(auth.getName());
        } catch (Exception e) {
            return null;
        }
    }
}
