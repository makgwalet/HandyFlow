package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.ControlStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.GrcLink;
import za.co.handyflow.platform.legalcompliance.domain.model.GrcLinkType;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.GrcDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Obligation ↔ Risk ↔ Control graph. Many-to-many in every direction: one control can address many obligations and risks,
 * one obligation can have many controls, and so on. Targets are checked here (they live in different tables); every link and unlink
 * is written to the audit log against both ends.
 */
@Service
@RequiredArgsConstructor
public class GrcLinkService {

    private final GrcLinkRepository links;
    private final RiskRepository risks;
    private final ControlRepository controls;
    private final RegulatoryObligationRepository obligations;
    private final RegulatoryAuditService audit;

    @Transactional
    public LinkItem link(TenantId tenantId, LinkRequest req, UUID userId, String userName) {
        Ends e = resolve(tenantId, req.type(), req.sourceId(), req.targetId());
        if (links.findExact(tenantId, req.type(), req.sourceId(), req.targetId()).isPresent())
            throw new HandyFlowException("These two items are already linked", HttpStatus.CONFLICT, "ALREADY_LINKED");
        GrcLink l = links.save(GrcLink.create(tenantId, req.type(), req.sourceId(), req.targetId(), req.note(), userId, userName));
        audit.record(tenantId.getValue(), e.sourceKind, req.sourceId(), "LINKED", e.targetKind + " " + e.targetLabel, userId, userName);
        audit.record(tenantId.getValue(), e.targetKind, req.targetId(), "LINKED", e.sourceKind + " " + e.sourceLabel, userId, userName);
        return new LinkItem(l.getId(), e.targetKind, req.targetId(), e.targetRef, e.targetLabel, e.targetStatus);
    }

    @Transactional
    public void unlink(TenantId tenantId, UUID linkId, UUID userId, String userName) {
        GrcLink l = links.findForTenant(tenantId, linkId).orElseThrow(() -> new ResourceNotFoundException("Link", linkId.toString()));
        links.delete(l);
        String sk = kinds(l.getLinkType())[0], tk = kinds(l.getLinkType())[1];
        audit.record(tenantId.getValue(), sk, l.getSourceId(), "UNLINKED", tk + " " + l.getTargetId(), userId, userName);
        audit.record(tenantId.getValue(), tk, l.getTargetId(), "UNLINKED", sk + " " + l.getSourceId(), userId, userName);
    }

    /** Everything linked to one risk, control or obligation, resolved to display values. */
    @Transactional(readOnly = true)
    public List<LinkItem> linksFor(TenantId tenantId, String kind, UUID id) {
        List<LinkItem> out = new ArrayList<>();
        switch (kind) {
            case "RISK" -> {
                links.findBySource(tenantId, GrcLinkType.RISK_CONTROL, id).forEach(l -> out.add(controlItem(tenantId, l.getId(), l.getTargetId())));
                links.findBySource(tenantId, GrcLinkType.RISK_OBLIGATION, id).forEach(l -> out.add(obligationItem(tenantId, l.getId(), l.getTargetId())));
            }
            case "CONTROL" -> {
                links.findByTarget(tenantId, GrcLinkType.RISK_CONTROL, id).forEach(l -> out.add(riskItem(tenantId, l.getId(), l.getSourceId())));
                links.findBySource(tenantId, GrcLinkType.CONTROL_OBLIGATION, id).forEach(l -> out.add(obligationItem(tenantId, l.getId(), l.getTargetId())));
            }
            case "OBLIGATION" -> {
                links.findByTarget(tenantId, GrcLinkType.RISK_OBLIGATION, id).forEach(l -> out.add(riskItem(tenantId, l.getId(), l.getSourceId())));
                links.findByTarget(tenantId, GrcLinkType.CONTROL_OBLIGATION, id).forEach(l -> out.add(controlItem(tenantId, l.getId(), l.getSourceId())));
            }
            default -> throw new HandyFlowException("Unknown kind " + kind, HttpStatus.BAD_REQUEST, "VALIDATION");
        }
        return out;
    }

    // ── internals ──

    private record Ends(String sourceKind, String targetKind, String sourceRef, String sourceLabel, String targetRef, String targetLabel, String targetStatus) {}

    private static String[] kinds(GrcLinkType t) {
        return switch (t) {
            case RISK_OBLIGATION -> new String[]{"RISK", "OBLIGATION"};
            case RISK_CONTROL -> new String[]{"RISK", "CONTROL"};
            case CONTROL_OBLIGATION -> new String[]{"CONTROL", "OBLIGATION"};
        };
    }

    private Ends resolve(TenantId tenantId, GrcLinkType type, UUID sourceId, UUID targetId) {
        String[] k = kinds(type);
        Item s = item(tenantId, k[0], sourceId), t = item(tenantId, k[1], targetId);
        if (s.closed) throw new HandyFlowException("The " + k[0].toLowerCase() + " is closed or retired and cannot be linked", HttpStatus.CONFLICT, "INVALID_STATE");
        if (t.closed) throw new HandyFlowException("The " + k[1].toLowerCase() + " is closed or retired and cannot be linked", HttpStatus.CONFLICT, "INVALID_STATE");
        return new Ends(k[0], k[1], s.ref, s.title, t.ref, t.title, t.status);
    }

    private record Item(String ref, String title, String status, boolean closed) {}

    private Item item(TenantId tenantId, String kind, UUID id) {
        return switch (kind) {
            case "RISK" -> risks.findForTenant(tenantId, id).map(r -> new Item(r.getReference(), r.getTitle(), r.getStatus().name(), !r.isOpen()))
                    .orElseThrow(() -> new ResourceNotFoundException("Risk", id.toString()));
            case "CONTROL" -> controls.findForTenant(tenantId, id).map(c -> new Item(c.getReference(), c.getTitle(), c.getStatus().name(), c.getStatus() == ControlStatus.RETIRED))
                    .orElseThrow(() -> new ResourceNotFoundException("Control", id.toString()));
            default -> obligations.findActiveById(tenantId, id).map(o -> new Item(o.getRegulationReference(), o.getTitle(), o.getStatus().name(), false))
                    .orElseThrow(() -> new ResourceNotFoundException("Obligation", id.toString()));
        };
    }

    private LinkItem riskItem(TenantId t, UUID linkId, UUID id) { return toItem(linkId, "RISK", id, safe(t, "RISK", id)); }
    private LinkItem controlItem(TenantId t, UUID linkId, UUID id) { return toItem(linkId, "CONTROL", id, safe(t, "CONTROL", id)); }
    private LinkItem obligationItem(TenantId t, UUID linkId, UUID id) { return toItem(linkId, "OBLIGATION", id, safe(t, "OBLIGATION", id)); }
    private static LinkItem toItem(UUID linkId, String kind, UUID id, Item i) { return new LinkItem(linkId, kind, id, i.ref, i.title, i.status); }

    /** An obligation may have been soft-deleted since linking: show it as removed rather than failing the whole read. */
    private Item safe(TenantId t, String kind, UUID id) {
        try { return item(t, kind, id); } catch (ResourceNotFoundException e) { return new Item(null, "(removed)", "REMOVED", true); }
    }
}
