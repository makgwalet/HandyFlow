package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultationAddendum;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationAddendumRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.dto.AddendumDtos.AddendumResponse;
import za.co.handyflow.platform.clinic.dto.TaskDtos.CreateTaskRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Append-only notes on a signed or locked consultation (S1-1). The original record is never changed. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicAddendumService {

    static final int MAX_LENGTH = 5000;

    private final ClinicConsultationRepository      consultationRepo;
    private final ClinicConsultationAddendumRepository addendumRepo;
    private final ClinicTaskService                    tasks;
    private final ClinicStaffNames                     staffNames;

    @Transactional
    public AddendumResponse add(TenantId tenantId, UUID consultationId, String text) { return add(tenantId, consultationId, text, null); }

    @Transactional
    public AddendumResponse add(TenantId tenantId, UUID consultationId, String text, String rawKind) {
        String kind = AddendumRules.kind(rawKind);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("An addendum cannot be empty.");
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("An addendum is limited to " + MAX_LENGTH + " characters.");
        }
        ClinicConsultation c = load(tenantId, consultationId);
        if (!c.isSigned() && !"LOCKED".equals(c.getStatus())) {
            throw new IllegalStateException("Addenda can only be added to a signed or locked consultation (is "
                    + c.getStatus() + "); edit the draft instead.");
        }
        UUID me = currentUserIdOrNull();
        ClinicConsultationAddendum a = ClinicConsultationAddendum.of(c, me, text.trim(), kind);
        addendumRepo.save(a);
        tellSigner(tenantId, c, a, me);
        return toResponse(a);
    }

    /** An addendum the system writes because of something done after signing (for example a late prescription). */
    @Transactional
    public void addSystem(TenantId tenantId, ClinicConsultation c, String text) {
        addendumRepo.save(ClinicConsultationAddendum.of(c, currentUserIdOrNull(), text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text, AddendumRules.LATE_MEDICINE));
    }

    @Transactional(readOnly = true)
    public List<AddendumResponse> list(TenantId tenantId, UUID consultationId) {
        load(tenantId, consultationId);
        return addendumRepo.findByConsultation(tenantId, consultationId).stream().map(this::toResponse).toList();
    }

    /** The person who signed the visit gets a task when someone else adds to it. A failure here never undoes the addendum. */
    private void tellSigner(TenantId tenantId, ClinicConsultation c, ClinicConsultationAddendum a, UUID author) {
        if (!AddendumRules.tellSigner(c.getSignedBy(), author)) return;
        try {
            String name = author == null ? null : staffNames.names(tenantId.getValue(), java.util.Set.of(author)).get(author);
            tasks.create(tenantId.getValue(), author, new CreateTaskRequest(c.getPatientId(), c.getSignedBy(), "GENERAL",
                    AddendumRules.noticeTitle(a.getKind()), AddendumRules.noticeDetail(name, a.getText()), null, "ADDENDUM", a.getId()));
        } catch (RuntimeException e) {
            log.warn("Could not tell the signer about addendum {}: {}", a.getId(), e.getMessage());
        }
    }

    private AddendumResponse toResponse(ClinicConsultationAddendum a) {
        return new AddendumResponse(a.getId(), a.getConsultationId(), a.getAuthorUserId(), a.getText(), a.getCreatedAt(), a.getKind(), AddendumRules.label(a.getKind()));
    }

    private ClinicConsultation load(TenantId tenantId, UUID id) {
        return consultationRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id.toString()));
    }

    private static UUID currentUserIdOrNull() {
        try {
            return za.co.handyflow.platform.shared.UserContext.getCurrentUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
