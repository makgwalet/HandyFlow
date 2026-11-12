// security/application/internal/GuardOfflineWriteService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.security.application.internal.GuardClientLedger.Kind;
import za.co.handyflow.platform.security.domain.model.AlarmEvent;
import za.co.handyflow.platform.security.domain.repository.AlarmEventRepository;
import za.co.handyflow.platform.security.dto.CreateIncidentRequest;
import za.co.handyflow.platform.security.dto.GateRegisterEntryResponse;
import za.co.handyflow.platform.security.dto.IncidentResponse;
import za.co.handyflow.platform.security.dto.LogArrivalRequest;
import za.co.handyflow.platform.security.dto.ScanRequest;
import za.co.handyflow.platform.security.dto.ScanResponse;
import za.co.handyflow.platform.security.dto.TriggerDuressRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Makes the three writes a guard's phone may repeat safe to repeat: a scan, an incident report and a panic press. With no
 * client id the call behaves exactly as before. With one, the first call does the work and remembers the id in the same
 * transaction; a retry returns the first result and creates nothing. Two retries racing each other cannot both win: the
 * second hits the unique key, rolls back, and is told to retry, at which point it replays the first.
 */
@Service
@RequiredArgsConstructor
public class GuardOfflineWriteService {

    private final GuardClientLedger ledger;
    private final CheckpointScanService scanService;
    private final IncidentService incidentService;
    private final ControlRoomService controlRoomService;
    private final AlarmEventRepository alarmEvents;
    private final IncidentCaseService caseService;
    private final EvidenceFacade evidenceFacade;
    private final GateAccessService gateAccessService;

    @Transactional
    public ScanResponse scan(TenantId tenantId, UUID guardId, ScanRequest req) {
        return once(tenantId, guardId, Kind.SCAN, req.clientRef(),
                () -> scanService.scan(tenantId, req, guardId), ScanResponse::logId,
                id -> scanService.describe(tenantId, id));
    }

    @Transactional
    public IncidentResponse incident(TenantId tenantId, UUID guardId, String guardName, CreateIncidentRequest req) {
        return once(tenantId, guardId, Kind.INCIDENT, req.clientRef(),
                () -> incidentService.createIncident(tenantId, req, guardId, guardName), IncidentResponse::id,
                id -> incidentService.getIncidentDetail(tenantId, id));
    }

    @Transactional
    public AlarmEvent duress(TenantId tenantId, UUID guardId, TriggerDuressRequest req) {
        return once(tenantId, guardId, Kind.DURESS, req.clientRef(),
                () -> controlRoomService.triggerDuress(tenantId, req), AlarmEvent::getId,
                id -> alarmEvents.findById(id).orElseThrow(() -> new ResourceNotFoundException("AlarmEvent", id.toString())));
    }

    /** A gate arrival. A repeat with the same clientRef returns the first entry instead of logging the visitor again. */
    @Transactional
    public GateRegisterEntryResponse gateArrival(TenantId tenantId, UUID guardId, UUID siteId, LogArrivalRequest req) {
        return once(tenantId, guardId, Kind.GATE_ENTRY, req.clientRef(),
                () -> gateAccessService.logArrivalByGuard(tenantId, guardId, siteId, req), GateRegisterEntryResponse::id,
                id -> gateAccessService.getEntry(tenantId, id));
    }

    /**
     * A photo for an incident the guard reported, named by the incident's clientRef because the phone may send the photo
     * before it knows the server's id. Until the incident has reached the server this answers 409 and the phone retries.
     * The photo's own id (photoRef) makes a retried upload a no-op.
     */
    @Transactional
    public EvidenceResponse evidence(TenantId tenantId, UUID guardId, String guardName, UUID incidentRef, UUID photoRef,
                                     MultipartFile file, String label) {
        UUID incidentId = ledger.find(tenantId, Kind.INCIDENT, incidentRef, guardId).orElseThrow(() -> new HandyFlowException(
                "The incident report has not reached the server yet. Try again shortly.", HttpStatus.CONFLICT, "INCIDENT_NOT_SENT_YET"));
        return once(tenantId, guardId, Kind.EVIDENCE, photoRef,
                () -> caseService.attachByGuard(tenantId, incidentId, guardId, file, label, guardName), EvidenceResponse::id,
                id -> evidenceFacade.listFor(tenantId, IncidentCaseService.SOURCE_MODULE, IncidentCaseService.ENTITY_TYPE, incidentId).stream()
                        .filter(e -> e.id().equals(id)).findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Evidence", id.toString())));
    }

    private <T> T once(TenantId tenantId, UUID guardId, Kind kind, UUID clientRef,
                       Supplier<T> create, Function<T, UUID> idOf, Function<UUID, T> replay) {
        if (clientRef == null) return create.get();
        Optional<UUID> first = ledger.find(tenantId, kind, clientRef, guardId);
        if (first.isPresent()) return replay.apply(first.get());
        T made = create.get();
        try {
            ledger.record(tenantId, guardId, kind, clientRef, idOf.apply(made));
        } catch (DataIntegrityViolationException raced) {
            throw new HandyFlowException("That request is already being processed. Try again in a moment.",
                    HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS");
        }
        return made;
    }
}
