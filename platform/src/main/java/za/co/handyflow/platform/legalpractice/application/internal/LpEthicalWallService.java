package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpClient;
import za.co.handyflow.platform.legalpractice.domain.model.LpEthicalWall;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatter;
import za.co.handyflow.platform.legalpractice.domain.repository.LpClientRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpEthicalWallRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpMatterRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpWallDenialRepository;
import za.co.handyflow.platform.legalpractice.dto.CreateWallRequest;
import za.co.handyflow.platform.legalpractice.dto.WallDenialResponse;
import za.co.handyflow.platform.legalpractice.dto.WallResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ethical walls. Putting one up or lifting one needs LEGALPRACTICE_WALL_MANAGE; once up, it binds everyone it names,
 * administrators included, because the services themselves refuse (see {@link LpWallGuard}). Every refusal is recorded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpEthicalWallService implements LpWallGuard {

    static final String DENIED = "You are not permitted to access this matter";

    private final LpEthicalWallRepository wallRepo;
    private final LpWallDenialRepository denialRepo;
    private final LpMatterRepository matterRepo;
    private final LpClientRepository clientRepo;
    private final LpWallDenialLogger denialLogger;

    // ---- managing walls ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<WallResponse> list(TenantId tenantId, boolean activeOnly, Pageable pageable) {
        Page<LpEthicalWall> page = wallRepo.list(tenantId, activeOnly, pageable);
        Map<UUID, String> labels = new HashMap<>();
        return page.map(w -> toResponse(tenantId, w, labels));
    }

    @Transactional
    public WallResponse create(TenantId tenantId, CreateWallRequest req, UUID by, String byName) {
        Instant now = Instant.now();
        LpEthicalWall wall;
        if ("MATTER".equals(req.scope())) {
            if (req.matterId() == null) throw new IllegalArgumentException("Choose the matter");
            matterRepo.findActiveById(tenantId, req.matterId()).orElseThrow(() -> new ResourceNotFoundException("LpMatter", req.matterId().toString()));
            if (wallRepo.countInForceOnMatter(tenantId, req.userId(), req.matterId()) > 0) {
                throw new IllegalStateException("This person is already walled off from that matter");
            }
            wall = LpEthicalWall.onMatter(tenantId, req.matterId(), req.userId(), req.userName(), req.reason(), by, byName, now);
        } else if ("CLIENT".equals(req.scope())) {
            if (req.clientId() == null) throw new IllegalArgumentException("Choose the client");
            clientRepo.findActiveById(tenantId, req.clientId()).orElseThrow(() -> new ResourceNotFoundException("LpClient", req.clientId().toString()));
            if (wallRepo.countInForceOnClient(tenantId, req.userId(), req.clientId()) > 0) {
                throw new IllegalStateException("This person is already walled off from that client");
            }
            wall = LpEthicalWall.onClient(tenantId, req.clientId(), req.userId(), req.userName(), req.reason(), by, byName, now);
        } else {
            throw new IllegalArgumentException("A wall is on a MATTER or on a CLIENT");
        }
        wallRepo.save(wall);
        log.info("Ethical wall {} up: scope={} user={} tenant={}", wall.getId(), wall.getScope(), wall.getUserId(), tenantId);
        return toResponse(tenantId, wall, new HashMap<>());
    }

    @Transactional
    public WallResponse lift(TenantId tenantId, UUID id, String reason, UUID by, String byName) {
        LpEthicalWall wall = wallRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("LpEthicalWall", id.toString()));
        wall.lift(by, byName, reason, Instant.now());
        wallRepo.save(wall);
        log.info("Ethical wall {} lifted by {} tenant={}", id, by, tenantId);
        return toResponse(tenantId, wall, new HashMap<>());
    }

    @Transactional(readOnly = true)
    public Page<WallDenialResponse> denials(TenantId tenantId, Pageable pageable) {
        Map<UUID, String> labels = new HashMap<>();
        return denialRepo.recent(tenantId, pageable).map(d -> new WallDenialResponse(d.getId(), d.getUserId(), d.getUserName(), d.getMatterId(),
                matterLabel(tenantId, d.getMatterId(), labels), d.getClientId(), d.getWallId(), d.getDeniedAt()));
    }

    // ---- the guard -----------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public void requireAccess(TenantId tenantId, LpMatter matter) {
        UUID user = signedInUser();
        if (user == null) return;
        for (LpEthicalWall w : wallRepo.findInForceForUser(tenantId, user)) {
            if (w.covers(matter.getId(), matter.getClientId())) {
                refuse(tenantId, user, matter.getId(), matter.getClientId(), w.getId());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireScopeAccess(TenantId tenantId, UUID matterId, UUID clientId) {
        UUID user = signedInUser();
        if (user == null) return;
        for (LpEthicalWall w : wallRepo.findInForceForUser(tenantId, user)) {
            if (w.covers(matterId, clientId)) {
                refuse(tenantId, user, matterId != null ? matterId : Barred.NONE_ID, clientId, w.getId());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireMatterAccess(TenantId tenantId, UUID matterId) {
        if (signedInUser() == null) return;
        matterRepo.findActiveById(tenantId, matterId).ifPresent(m -> requireAccess(tenantId, m));
    }

    @Override
    @Transactional(readOnly = true)
    public void requireClientAccess(TenantId tenantId, UUID clientId) {
        UUID user = signedInUser();
        if (user == null) return;
        for (LpEthicalWall w : wallRepo.findInForceForUser(tenantId, user)) {
            if ("CLIENT".equals(w.getScope()) && w.getClientId().equals(clientId)) {
                refuseClient(tenantId, user, clientId, w.getId());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Barred barred(TenantId tenantId) {
        UUID user = signedInUser();
        List<UUID> matters = new ArrayList<>(), clients = new ArrayList<>();
        if (user != null) {
            for (LpEthicalWall w : wallRepo.findInForceForUser(tenantId, user)) {
                if ("MATTER".equals(w.getScope())) matters.add(w.getMatterId()); else clients.add(w.getClientId());
            }
        }
        return Barred.of(matters, clients);
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** The signed-in user, or null when there is none (a background job): those are not subject to a wall. */
    private static UUID signedInUser() {
        String id = TenantContext.getUserId();
        if (id == null || id.isBlank()) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void refuse(TenantId tenantId, UUID user, UUID matterId, UUID clientId, UUID wallId) {
        record(tenantId, user, matterId, clientId, wallId);
        throw new AccessDeniedException(DENIED);
    }

    private void refuseClient(TenantId tenantId, UUID user, UUID clientId, UUID wallId) {
        // A client-level request has no matter; the record carries the client's wall and the nil matter id.
        record(tenantId, user, Barred.NONE_ID, clientId, wallId);
        throw new AccessDeniedException("You are not permitted to access this client");
    }

    private void record(TenantId tenantId, UUID user, UUID matterId, UUID clientId, UUID wallId) {
        try {
            denialLogger.record(tenantId, user, TenantContext.getCurrentUserName(), matterId, clientId, wallId);
        } catch (RuntimeException e) {
            // The refusal stands even if the record could not be written.
            log.error("Could not record a wall refusal for user={} matter={}", user, matterId, e);
        }
    }

    private WallResponse toResponse(TenantId tenantId, LpEthicalWall w, Map<UUID, String> labels) {
        String target = "MATTER".equals(w.getScope()) ? matterLabel(tenantId, w.getMatterId(), labels) : clientLabel(tenantId, w.getClientId(), labels);
        return new WallResponse(w.getId(), w.getScope(), w.getMatterId(), w.getClientId(), target, w.getUserId(), w.getUserName(), w.getReason(),
                w.getCreatedByName(), w.getCreatedAt(), w.active(), w.getLiftedByName(), w.getLiftedAt(), w.getLiftReason());
    }

    private String matterLabel(TenantId tenantId, UUID matterId, Map<UUID, String> cache) {
        return cache.computeIfAbsent(matterId, id -> matterRepo.findActiveById(tenantId, id)
                .map(m -> m.getMatterNumber() + " " + m.getMatterName()).orElse("(matter not found)"));
    }

    private String clientLabel(TenantId tenantId, UUID clientId, Map<UUID, String> cache) {
        return cache.computeIfAbsent(clientId, id -> clientRepo.findActiveById(tenantId, id).map(LpClient::getName).orElse("(client not found)"));
    }
}
