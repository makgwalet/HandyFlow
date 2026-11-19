package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.Control;
import za.co.handyflow.platform.legalcompliance.domain.model.ControlStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.GrcLinkType;
import za.co.handyflow.platform.legalcompliance.domain.repository.ControlRepository;
import za.co.handyflow.platform.legalcompliance.dto.GrcDtos.*;
import za.co.handyflow.platform.notifications.domain.repository.GrcLinkRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Control catalogue. A control is DRAFT until it has an owner, objective, procedure, expected evidence and at least one linked risk or
 * obligation. ACTIVE means "in operation", not "effective": operating effectiveness stays NOT_TESTED until a reviewed test says otherwise.
 */
@Service
@RequiredArgsConstructor
public class ControlService {

    private static final String CONTROL = "CONTROL";

    private final ControlRepository controls;
    private final GrcLinkRepository links;
    private final GrcLinkService linkService;
    private final RegulatoryAuditService audit;

    @Value("${app.legalcompliance.sod.enforce:false}")
    private boolean enforceSegregationOfDuties;

    @Transactional
    public ControlResponse create(TenantId tenantId, ControlRequest r, UUID userId, String userName) {
        Control c = guarded(() -> Control.create(tenantId, String.format("CTL-%05d", controls.nextRef()), r.title(), r.objective(), r.procedure(), r.controlType(),
                r.nature(), r.frequency(), r.ownerId(), r.ownerName(), r.expectedEvidence(), userId));
        controls.save(c);
        audit.record(tenantId.getValue(), CONTROL, c.getId(), "CREATED", c.getReference() + " " + c.getTitle(), userId, userName);
        return view(tenantId, c);
    }

    @Transactional
    public ControlResponse update(TenantId tenantId, UUID id, ControlRequest r, UUID userId, String userName) {
        Control c = load(tenantId, id);
        boolean reset = guarded(() -> c.update(r.title(), r.objective(), r.procedure(), r.controlType(), r.nature(), r.frequency(), r.ownerId(), r.ownerName(), r.expectedEvidence()));
        controls.save(c);
        audit.record(tenantId.getValue(), CONTROL, id, "UPDATED", reset ? "Definition changed — design assessment reset to NOT_ASSESSED" : "Details updated", userId, userName);
        return view(tenantId, c);
    }

    @Transactional(readOnly = true)
    public List<ControlResponse> list(TenantId tenantId, ControlStatus status, boolean includeRetired) {
        var byObligation = links.findAllOfType(tenantId, GrcLinkType.CONTROL_OBLIGATION);
        var byRisk = links.findAllOfType(tenantId, GrcLinkType.RISK_CONTROL);
        return controls.search(tenantId, status, includeRetired).stream().map(c -> ControlResponse.of(c,
                (int) byObligation.stream().filter(l -> l.getSourceId().equals(c.getId())).count(),
                (int) byRisk.stream().filter(l -> l.getTargetId().equals(c.getId())).count())).toList();
    }

    @Transactional(readOnly = true)
    public ControlDetailResponse detail(TenantId tenantId, UUID id) {
        Control c = load(tenantId, id);
        return new ControlDetailResponse(view(tenantId, c), linkService.linksFor(tenantId, CONTROL, id));
    }

    @Transactional
    public ControlResponse activate(TenantId tenantId, UUID id, UUID userId, String userName) {
        Control c = load(tenantId, id);
        if (linkCount(tenantId, id) == 0)
            throw conflict("NOT_LINKED", "Link the control to at least one obligation or risk it addresses before activating it");
        guarded(() -> { c.activate(); return null; });
        controls.save(c);
        audit.record(tenantId.getValue(), CONTROL, id, "ACTIVATED", "Design " + c.getDesignRating() + "; operating effectiveness " + c.getOperatingEffectiveness(), userId, userName);
        return view(tenantId, c);
    }

    @Transactional
    public ControlResponse assessDesign(TenantId tenantId, UUID id, DesignAssessmentRequest req, UUID userId, String userName) {
        Control c = load(tenantId, id);
        if (enforceSegregationOfDuties && userId != null && userId.equals(c.getOwnerId()))
            throw new HandyFlowException("Segregation of duties: the control owner cannot assess the design of their own control", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
        guarded(() -> { c.assessDesign(req.rating(), req.note(), userId, userName); return null; });
        controls.save(c);
        audit.record(tenantId.getValue(), CONTROL, id, "DESIGN_ASSESSED", req.rating() + (req.note() == null || req.note().isBlank() ? "" : ": " + req.note().trim()), userId, userName);
        return view(tenantId, c);
    }

    @Transactional
    public ControlResponse retire(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, String userName) {
        Control c = load(tenantId, id);
        guarded(() -> { c.retire(req.reason()); return null; });
        controls.save(c);
        audit.record(tenantId.getValue(), CONTROL, id, "RETIRED", req.reason().trim() + " (" + linkCount(tenantId, id) + " link(s) retained for history)", userId, userName);
        return view(tenantId, c);
    }

    private int linkCount(TenantId tenantId, UUID controlId) {
        return links.findByTarget(tenantId, GrcLinkType.RISK_CONTROL, controlId).size() + links.findBySource(tenantId, GrcLinkType.CONTROL_OBLIGATION, controlId).size();
    }

    private ControlResponse view(TenantId tenantId, Control c) {
        return ControlResponse.of(c, links.findBySource(tenantId, GrcLinkType.CONTROL_OBLIGATION, c.getId()).size(), links.findByTarget(tenantId, GrcLinkType.RISK_CONTROL, c.getId()).size());
    }

    private Control load(TenantId tenantId, UUID id) {
        return controls.findForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Control", id.toString()));
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw conflict("INVALID_STATE", e.getMessage()); }
    }
    private static HandyFlowException conflict(String code, String m) { return new HandyFlowException(m, HttpStatus.CONFLICT, code); }
}
