package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.SitePermissionRules;
import za.co.handyflow.platform.trainingprovider.dto.SitePermissionDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Site delivery permissions: the provider's own record of who may deliver, assess or moderate at which site. It does not replace the
 * appointment checks and is not a regulatory approval. Withdrawn with a reason, never deleted.
 */
@Service
@RequiredArgsConstructor
public class TrainProvSitePermissionService {

    private final TrainProvSitePermissionRepository permissions;
    private final TrainProvPractitionerRepository practitioners;
    private final TrainProvDeliverySiteRepository sites;
    private final TrainProvProgrammeRepository programmes;

    public List<PermissionView> list(TenantId t, UUID siteId, UUID practitionerId, String status, LocalDate today) {
        return permissions.search(t, siteId, practitionerId, status == null || status.isBlank() ? null : status.trim()).stream().<PermissionView>map(p -> view(t, p, today)).toList();
    }

    @Transactional
    public PermissionView create(TenantId t, PermissionRequest r, UUID by, LocalDate today) {
        TrainProvPractitioner person = practitioners.findForTenant(t, r.practitionerId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvPractitioner", r.practitionerId().toString()));
        TrainProvDeliverySite site = sites.findForTenant(t, r.siteId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvDeliverySite", r.siteId().toString()));
        if (r.programmeId() != null && programmes.findForTenant(t, r.programmeId()).isEmpty()) {
            throw new ResourceNotFoundException("TrainProvProgramme", r.programmeId().toString());
        }
        String problem = SitePermissionRules.problem(r.role(), "ACTIVE".equals(person.getStatus()), "ACTIVE".equals(site.getStatus()), r.validFrom(), r.validTo(), r.basis());
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "TP_SITE_PERMISSION_INVALID");
        }
        if (permissions.liveExists(t, person.getId(), site.getId(), r.role(), r.programmeId())) {
            throw new HandyFlowException("This person already has a live permission for that site, role and programme. Withdraw it first to replace it.", HttpStatus.CONFLICT,
                    "TP_SITE_PERMISSION_STATE");
        }
        TrainProvSitePermission p = TrainProvSitePermission.create(t, person.getId(), site.getId(), r.role(), r.programmeId(), r.validFrom(), r.validTo(), r.basis(), by);
        permissions.save(p);
        return view(t, p, today);
    }

    @Transactional
    public PermissionView withdraw(TenantId t, UUID id, String reason, UUID by, LocalDate today) {
        TrainProvSitePermission p = permissions.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvSitePermission", id.toString()));
        guard(() -> { p.withdraw(reason, by); return null; });
        permissions.save(p);
        return view(t, p, today);
    }

    /** Whether the recorded permissions cover a person in a role at a site for a programme on a day. A prompt, never a finding. */
    public String cover(TenantId t, UUID practitionerId, UUID siteId, String role, UUID programmeId, LocalDate day) {
        return SitePermissionRules.coverOn(permissions.liveFor(t, practitionerId, siteId, role).stream().map(TrainProvSitePermission::asRule).toList(), programmeId, day);
    }

    private PermissionView view(TenantId t, TrainProvSitePermission p, LocalDate today) {
        TrainProvPractitioner person = practitioners.findForTenant(t, p.getPractitionerId()).orElse(null);
        boolean inForce = p.isActive() && !today.isBefore(p.getValidFrom()) && (p.getValidTo() == null || !today.isAfter(p.getValidTo()));
        return new PermissionView(p.getId(), p.getPractitionerId(), person == null ? null : person.getPractitionerNo(), person == null ? null : person.fullName(), p.getSiteId(),
                sites.findForTenant(t, p.getSiteId()).map(TrainProvDeliverySite::getName).orElse(null), p.getRole(), p.getProgrammeId(),
                p.getProgrammeId() == null ? null : programmes.findForTenant(t, p.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse(null), p.getValidFrom(), p.getValidTo(),
                p.getBasis(), p.getStatus(), p.getWithdrawnReason(), inForce, p.getCreatedAt());
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_SITE_PERMISSION_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_SITE_PERMISSION_INVALID");
        }
    }
}
