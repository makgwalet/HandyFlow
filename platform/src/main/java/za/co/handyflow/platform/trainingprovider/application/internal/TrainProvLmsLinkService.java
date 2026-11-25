package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLmsLink;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgramme;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgrammeVersion;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLmsLinkRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeVersionRepository;
import za.co.handyflow.platform.trainingprovider.dto.LmsLinkDtos.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Links to an outside learning system. One active link per programme version. No secret is held and nothing is synced. */
@Service
@RequiredArgsConstructor
public class TrainProvLmsLinkService {

    private final TrainProvLmsLinkRepository links;
    private final TrainProvProgrammeVersionRepository versions;
    private final TrainProvProgrammeRepository programmes;

    public List<LinkView> list(TenantId t, UUID versionId, String status) {
        return links.search(t, versionId, blank(status)).stream().<LinkView>map(l -> view(t, l)).toList();
    }

    @Transactional
    public LinkView create(TenantId t, LinkRequest r, UUID by) {
        TrainProvProgrammeVersion v = versions.findForTenant(t, r.programmeVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeVersion", r.programmeVersionId().toString()));
        if ("SUPERSEDED".equals(v.getStatus())) {
            throw new HandyFlowException("That programme version has been superseded. Add the link to the current version", HttpStatus.CONFLICT, "TP_LMS_STATE");
        }
        if (links.activeExists(t, v.getId())) {
            throw new HandyFlowException("This version already has an active link. End it with a reason before adding another", HttpStatus.CONFLICT, "TP_LMS_DUPLICATE");
        }
        TrainProvLmsLink l = guard(() -> TrainProvLmsLink.create(t, v.getId(), r.systemName(), r.launchUrl(), r.notes(), by));
        links.save(l);
        return view(t, l);
    }

    @Transactional
    public LinkView update(TenantId t, UUID id, LinkUpdate r) {
        TrainProvLmsLink l = find(t, id);
        guard(() -> { l.change(r.systemName(), r.launchUrl(), r.notes()); return null; });
        links.save(l);
        return view(t, l);
    }

    @Transactional
    public LinkView end(TenantId t, UUID id, String reason, UUID by) {
        TrainProvLmsLink l = find(t, id);
        guard(() -> { l.end(reason, by); return null; });
        links.save(l);
        return view(t, l);
    }

    private LinkView view(TenantId t, TrainProvLmsLink l) {
        TrainProvProgrammeVersion v = versions.findForTenant(t, l.getProgrammeVersionId()).orElse(null);
        String title = v == null ? null : programmes.findForTenant(t, v.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse(null);
        return new LinkView(l.getId(), l.getProgrammeVersionId(), v == null ? null : v.getVersionNo(), v == null ? null : v.getProgrammeId(), title, l.getSystemName(),
                l.getLaunchUrl(), l.getNotes(), l.getStatus(), l.getEndedReason(), l.getCreatedAt());
    }

    private TrainProvLmsLink find(TenantId t, UUID id) {
        return links.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLmsLink", id.toString()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_LMS_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_LMS_INVALID");
        }
    }
}
