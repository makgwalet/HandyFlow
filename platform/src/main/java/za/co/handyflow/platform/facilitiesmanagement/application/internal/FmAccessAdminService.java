package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClientAccess;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientAccessRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmClientAccessResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Manage which clients a staff user is restricted to. An empty set removes the restriction (the user
 * becomes unrestricted again). {@code userId} is not validated against the identity module — callers are
 * ADMIN-only. Every change is audited with the before and after sets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmAccessAdminService {

    private final FmClientAccessRepository clientAccessRepository;
    private final FmClientRepository clientRepository;
    private final FmAuditService auditService;

    @Transactional(readOnly = true)
    public FmClientAccessResponse getClientAccess(TenantId tenantId, UUID userId) {
        return toResponse(userId, clientAccessRepository.findClientIds(tenantId, userId));
    }

    @Transactional
    public FmClientAccessResponse setClientAccess(TenantId tenantId, UUID userId, Set<UUID> clientIds) {
        for (UUID clientId : clientIds) {
            clientRepository.findActiveById(tenantId, clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("FmClient", clientId.toString()));
        }
        List<UUID> before = clientAccessRepository.findClientIds(tenantId, userId);

        clientAccessRepository.deleteForUser(tenantId, userId);
        UUID grantedBy = TenantContext.getCurrentUserId();
        for (UUID clientId : clientIds) {
            clientAccessRepository.save(FmClientAccess.grant(tenantId, userId, clientId, grantedBy));
        }

        auditService.record(tenantId, "CLIENT_ACCESS_CHANGED", "USER", userId,
                "before=" + sorted(before) + " after=" + sorted(List.copyOf(clientIds)));
        log.info("FM client access for user={} tenant={} set to {} client(s)", userId, tenantId, clientIds.size());
        return toResponse(userId, List.copyOf(clientIds));
    }

    private FmClientAccessResponse toResponse(UUID userId, List<UUID> clientIds) {
        return new FmClientAccessResponse(userId, !clientIds.isEmpty(), sorted(clientIds));
    }

    private List<UUID> sorted(List<UUID> ids) {
        return ids.stream().sorted(Comparator.naturalOrder()).toList();
    }
}