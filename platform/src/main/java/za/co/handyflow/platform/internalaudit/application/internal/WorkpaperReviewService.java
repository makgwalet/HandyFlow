package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.internalaudit.domain.model.AuditReviewNote;
import za.co.handyflow.platform.internalaudit.domain.model.AuditWorkpaperFile;
import za.co.handyflow.platform.internalaudit.domain.model.AuditWorkpaperLink;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.internalaudit.dto.WorkpaperReviewDtos.LinkResponse;
import za.co.handyflow.platform.internalaudit.dto.WorkpaperReviewDtos.NoteResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Review notes on workpapers (raise, answer, clear or return) and links from a workpaper to the test, exception or finding it supports. */
@Service
@RequiredArgsConstructor
public class WorkpaperReviewService {

    private final AuditReviewNoteRepository noteRepo;
    private final AuditWorkpaperLinkRepository linkRepo;
    private final AuditWorkpaperFileRepository fileRepo;
    private final AuditTestRepository testRepo;
    private final AuditExceptionRepository exceptionRepo;
    private final AuditFindingRepository findingRepo;
    private final AuditTrailFacade trail;

    private AuditWorkpaperFile file(TenantId t, UUID id) {
        return fileRepo.findByTenantAndId(t.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("WorkpaperFile", id.toString()));
    }
    private AuditReviewNote note(TenantId t, UUID id) {
        return noteRepo.findByTenantAndId(t.getValue(), id).orElseThrow(() -> new ResourceNotFoundException("ReviewNote", id.toString()));
    }

    @Transactional
    public NoteResponse raise(TenantId t, UUID workpaperId, String text, UUID by) {
        AuditWorkpaperFile f = file(t, workpaperId);
        AuditReviewNote n;
        try {
            n = AuditReviewNote.raise(t.getValue(), f.getEngagementId(), workpaperId, text, by);
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "INVALID_NOTE");
        }
        noteRepo.save(n);
        trail.record(t, "REVIEW_NOTE_RAISED", "REVIEW_NOTE", n.getId(), null, Map.of("workpaperId", workpaperId.toString()));
        return toResponse(n);
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> forWorkpaper(TenantId t, UUID workpaperId) {
        return noteRepo.findByWorkpaper(t.getValue(), workpaperId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public NoteResponse respond(TenantId t, UUID noteId, String reply, UUID by) {
        AuditReviewNote n = note(t, noteId);
        return change(t, n, "REVIEW_NOTE_ANSWERED", () -> n.respond(by, reply));
    }

    @Transactional
    public NoteResponse clear(TenantId t, UUID noteId, UUID by) {
        AuditReviewNote n = note(t, noteId);
        return change(t, n, "REVIEW_NOTE_CLEARED", () -> n.clear(by));
    }

    @Transactional
    public NoteResponse returnNote(TenantId t, UUID noteId, String why, UUID by) {
        AuditReviewNote n = note(t, noteId);
        return change(t, n, "REVIEW_NOTE_RETURNED", () -> n.returnWithComment(by, why));
    }

    private NoteResponse change(TenantId t, AuditReviewNote n, String action, Runnable doIt) {
        String before = n.getStatus();
        try {
            doIt.run();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATUS");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "INVALID_NOTE");
        }
        noteRepo.save(n);
        trail.record(t, action, "REVIEW_NOTE", n.getId(), Map.of("status", before), Map.of("status", n.getStatus()));
        return toResponse(n);
    }

    /** Throws if the workpaper still has review notes that are not cleared. Called before sign-off. */
    public void requireNotesCleared(TenantId t, UUID workpaperId) {
        long open = noteRepo.findByWorkpaper(t.getValue(), workpaperId).stream().filter(AuditReviewNote::blocksSignOff).count();
        if (open > 0) {
            throw new HandyFlowException(open + " review note" + (open == 1 ? "" : "s") + " on this workpaper " + (open == 1 ? "is" : "are")
                    + " not cleared yet. Clear them before signing off.", HttpStatus.CONFLICT, "REVIEW_NOTES_OPEN");
        }
    }

    // ── links ───────────────────────────────────────────────────────────────
    @Transactional
    public LinkResponse link(TenantId t, UUID workpaperId, String type, UUID targetId, UUID by) {
        file(t, workpaperId);
        boolean exists = switch (type == null ? "" : type) {
            case "TEST" -> testRepo.findByTenantAndId(t.getValue(), targetId).isPresent();
            case "EXCEPTION" -> exceptionRepo.findByTenantAndId(t.getValue(), targetId).isPresent();
            case "FINDING" -> findingRepo.findByTenantAndId(t.getValue(), targetId).isPresent();
            default -> throw new HandyFlowException("A workpaper can be linked to a test, an exception or a finding", HttpStatus.BAD_REQUEST, "BAD_LINK_TYPE");
        };
        if (!exists) throw new ResourceNotFoundException(type, targetId.toString());
        if (linkRepo.find(t.getValue(), workpaperId, type, targetId).isPresent()) {
            throw new HandyFlowException("Already linked", HttpStatus.CONFLICT, "ALREADY_LINKED");
        }
        AuditWorkpaperLink l = linkRepo.save(AuditWorkpaperLink.of(t.getValue(), workpaperId, type, targetId, by));
        trail.record(t, "WORKPAPER_LINKED", "WORKPAPER", workpaperId, null, Map.of("type", type, "id", targetId.toString()));
        return new LinkResponse(l.getId(), workpaperId, type, targetId, l.getLinkedAt());
    }

    @Transactional
    public void unlink(TenantId t, UUID workpaperId, UUID linkRowId) {
        AuditWorkpaperLink l = linkRepo.findById(linkRowId).filter(x -> x.getTenantId().equals(t.getValue()) && x.getWorkpaperId().equals(workpaperId))
                .orElseThrow(() -> new ResourceNotFoundException("WorkpaperLink", linkRowId.toString()));
        linkRepo.delete(l);
        trail.record(t, "WORKPAPER_UNLINKED", "WORKPAPER", workpaperId, Map.of("type", l.getLinkType(), "id", l.getLinkId().toString()), null);
    }

    @Transactional(readOnly = true)
    public List<LinkResponse> linksFor(TenantId t, UUID workpaperId) {
        return linkRepo.findByWorkpaper(t.getValue(), workpaperId).stream()
                .map(l -> new LinkResponse(l.getId(), l.getWorkpaperId(), l.getLinkType(), l.getLinkId(), l.getLinkedAt())).toList();
    }

    /** Workpapers that support a given test, exception or finding. */
    @Transactional(readOnly = true)
    public List<LinkResponse> workpapersFor(TenantId t, String type, UUID targetId) {
        return linkRepo.findByTarget(t.getValue(), type, targetId).stream()
                .map(l -> new LinkResponse(l.getId(), l.getWorkpaperId(), l.getLinkType(), l.getLinkId(), l.getLinkedAt())).toList();
    }

    private NoteResponse toResponse(AuditReviewNote n) {
        return new NoteResponse(n.getId(), n.getWorkpaperId(), n.getNote(), n.getRaisedBy(), n.getRaisedAt(), n.getStatus(), n.getResponse(),
                n.getRespondedAt(), n.getClearedAt(), n.getTimesReturned());
    }
}
